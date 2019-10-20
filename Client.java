package Project;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Client {
	public static void main(String args[]) throws Exception{
	//Reading File; NOTE: Please provide full path of the data file to FileReader and Scanner class
	/* Using FileReader to get number of data-vectors */
	FileReader dataFile = new FileReader("C:\\Users\\Simran\\eclipse-workspace\\Lab_640\\src\\Project\\data2.txt");
	LineNumberReader lnr = new LineNumberReader(dataFile);
	/* Scanning the input file for further computation on file elements */
	Scanner input = new Scanner(new File("C:\\Users\\Simran\\eclipse-workspace\\Lab_640\\src\\Project\\data2.txt"));
	
	final int INITIAL_SEQ_NUM = 0;
	//Calculate number of Data Vectors from file
	int numOfDataVectors = numOfDataVectors(lnr);
	float[][] rawDataArray = rawDataArray(input,numOfDataVectors);

	//logic to convert data vectors into byte sequence	
	int toInt, toInt1;
	byte[] firstCoordinate = new byte[4];
	byte[] secondCoordinate = new byte[4];
	byte[] theDataVector = new byte[8];
	ByteArrayOutputStream dataVecStream = new ByteArrayOutputStream();
	int row;
	int seqNum = INITIAL_SEQ_NUM;
	InetAddress ClientAddress = InetAddress.getLocalHost();
	DatagramSocket myClient = new DatagramSocket();
	
	System.out.printf("Client is sending Data values for K-Means Clustering with %d data vectors\n",numOfDataVectors);
	for (row = 0; row < numOfDataVectors; row++ )
	{
		float temp = 0;
		float temp1 = 0;
				
		temp = rawDataArray[row][0];
		temp1 = rawDataArray[row][1];
		
		toInt = (int)(temp *100);
		toInt1 = (int)(temp1 *100);
		firstCoordinate = IntToByteArray(toInt);
		secondCoordinate = IntToByteArray(toInt1);
			
		/* Combining the two coordinates' bytes as 1 data vector 
		 * by copying both of them to a new byte array in network byte order */
		System.arraycopy(firstCoordinate, 0, theDataVector, 0, 4);
		System.arraycopy(secondCoordinate, 0, theDataVector, 4, 4);
		
		/* writing in buffer to keep counting the 8 bytes of data vector until it reaches 50 */
		dataVecStream.write(theDataVector);
		int vectorLen = (dataVecStream.size()/8);
		if (vectorLen == 50 || (vectorLen < 50 && row+1 == numOfDataVectors))
		{
			packetSender(vectorLen,dataVecStream,seqNum, ClientAddress,myClient);
			seqNum++;
			
		}
	}

	//For REQ packet and RACK packet
	int REQPacketCounter = 0;
	int defTimerForREQPacket = 1000;
	int numOfRackTries = 0;
	while(true)
	{
		/* The packet to be sent after all the data vectors are sent */
		byte[] REQPacket = new byte[1];
		REQPacket = packetType(0x02);
		DatagramPacket UDPREQPacket = new DatagramPacket(REQPacket, REQPacket.length,ClientAddress,1234 );
		//myClient.send(UDPREQPacket);
		myClient.setSoTimeout(defTimerForREQPacket);
		REQPacketCounter++;
		try
		{
			/* Receiving RACK packet */
			byte[] rcvRACKData = new byte[3];
			DatagramPacket rcvRACKPacket = new DatagramPacket(rcvRACKData,rcvRACKData.length );
			myClient.receive(rcvRACKPacket);
			numOfRackTries++;
			rcvRACKData = rcvRACKPacket.getData();
			int recvRACKPacketType = extractPacketType(rcvRACKData);
			if (recvRACKPacketType == 0x03)
			{
				
				break;
			}
			else if (numOfRackTries <5)
			{
				System.out.println("Invalid Packet Type");
				continue;
			}
			else
			{
				System.exit(0);
			}
		}
		catch (SocketTimeoutException execp)
		{
			if (REQPacketCounter<5)
			{
				/* Continuing to send packets again until 4th count 
				 * doubling the timer value everytime*/
				defTimerForREQPacket = defTimerForREQPacket*2;
				continue;
			}
			else
			{
				System.out.printf("Client socket timeout! Exception message while trying to receive RACK packet: %s",execp.getMessage());
				System.exit(0); 
			}
		}
	}
	
	/* For CLUS Packet */
	int defTimerCLUSPacket = 30000;
	byte[] rcvCLUSData = new byte[17];
	while(true)
	{
		System.out.println("Receiving CLUS Packet");
		myClient.setSoTimeout(defTimerCLUSPacket);
		DatagramPacket rcvCLUSPacket = new DatagramPacket(rcvCLUSData,rcvCLUSData.length );
		myClient.receive(rcvCLUSPacket);
		rcvCLUSData = rcvCLUSPacket.getData();
		
		int CLUSPacketType = extractPacketType(rcvCLUSData);
		try
		{
			if (CLUSPacketType != 0x04)
			{
				break;
			}
			else
			{
				System.out.println("Sending CACK...");
				byte[] CACKPacket = new byte[1];
				CACKPacket = packetType(0x05);
				DatagramPacket UDPCACKPacket = new DatagramPacket(CACKPacket, CACKPacket.length,ClientAddress,1234 );
				myClient.send(UDPCACKPacket);
				/* Extracting the 2D array from the received packet and display on the screen */
				
				float[][] CLUSDataVector1 = new float[1][2];
				float[][] CLUSDataVector2 = new float[1][2];
				byte[] Cluster1x = new byte[4];
				byte[] Cluster1y = new byte[4];
				byte[] Cluster2x = new byte[4];
				byte[] Cluster2y = new byte[4];
				
				System.arraycopy(rcvCLUSData, 1, Cluster1x, 0, 4);
				int num1 = (Cluster1x[0] & 0xff) << 24 | (Cluster1x[1] & 0xff) << 16 | (Cluster1x[2] & 0xff) << 8 | (Cluster1x[3] & 0xff);
				CLUSDataVector1[0][0] = ((float)num1)/100;
				
				System.arraycopy(rcvCLUSData, 5, Cluster1y, 0, 4);
				int num2 = (Cluster1y[0] & 0xff) << 24 | (Cluster1y[1] & 0xff) << 16 | (Cluster1y[2] & 0xff) << 8 | (Cluster1y[3] & 0xff);
				CLUSDataVector1[0][1] = ((float)num2)/100;
				
				System.arraycopy(rcvCLUSData, 9, Cluster2x, 0, 4);
				int num3 = (Cluster2x[0] & 0xff) << 24 | (Cluster2x[1] & 0xff) << 16 | (Cluster2x[2] & 0xff) << 8 | (Cluster2x[3] & 0xff);
				CLUSDataVector2[0][0] = ((float)num3)/100;
				
				System.arraycopy(rcvCLUSData, 13, Cluster2y, 0, 4);
				int num4 = (Cluster2y[0] & 0xff) << 24 | (Cluster2y[1] & 0xff) << 16 | (Cluster2y[2] & 0xff) << 8 | (Cluster2y[3] & 0xff);
				CLUSDataVector2[0][1] = ((float)num4)/100;
					
				
				for (int arrIndex=0; arrIndex<1; arrIndex++)
				{
					System.out.println("The first centroid is : ("+CLUSDataVector1[arrIndex][0]+","+CLUSDataVector1[arrIndex][1]+")");
					System.out.println("The second centroid is : ("+CLUSDataVector2[arrIndex][0]+","+CLUSDataVector2[arrIndex][1]+")");
				}
				
				System.out.println("Existing System");
				myClient.close();
				System.exit(0);
			}
		}
		catch (SocketTimeoutException execp)
		{
			System.out.printf("Client socket timeout! Exception message at CLUS: %s",execp.getMessage());
			System.exit(0);
			
		}
		
	}
	
	//TODO: close scanner and bufferedReader class
	}
	
	private static void packetSender(int vectorLen, ByteArrayOutputStream dataVecStream, int seqNum, InetAddress ClientAddress, DatagramSocket myClient) throws Exception
	{
		int dataPacketType = 0X00;
		//Writing up to 50 data packets in one buffer of byte array type
		byte[] dataVectorArr = new byte[vectorLen];
		dataVectorArr = dataVecStream.toByteArray();
		/* Resetting the output stream */
		dataVecStream.reset();
		
		//Constructing data packet
		byte[] finalDataPacket = new byte[vectorLen + 5];
		ByteArrayOutputStream finalDataBuffer = new ByteArrayOutputStream();
		int counter = 0;
		finalDataBuffer.write(packetType(dataPacketType));
		finalDataBuffer.write(twoBytePacket(seqNum));

		finalDataBuffer.write(twoBytePacket(vectorLen));
		finalDataBuffer.write(dataVectorArr);
		finalDataPacket = finalDataBuffer.toByteArray();
		int defTimerForDataVectorPackets = 1000;
		DatagramPacket finalPacket = new DatagramPacket(finalDataPacket, finalDataPacket.length,ClientAddress,1234 );
		byte[] rcvData = new byte[3];
		DatagramPacket rcvPacket = new DatagramPacket(rcvData,rcvData.length );
		
		vectorLen = 0;
		 
		while(true)
		{
			//Sending Data Packets...
			myClient.send(finalPacket);
			myClient.setSoTimeout(defTimerForDataVectorPackets);
			counter++;
			try
			{
				//Receving DACK Packet...
				myClient.receive(rcvPacket);
				byte[] newbuf = new byte[2];
				byte[] rcvBuf = new byte[3];
				rcvBuf = rcvPacket.getData();
				newbuf = Arrays.copyOfRange(rcvBuf, 1, 3);
				int rcvSeqNum;
				rcvSeqNum =  (newbuf[0] & 0xff) << 8 | (newbuf[1] & 0xff);
				
				if (rcvSeqNum == seqNum)
				{
					counter = 0;
					break;
				}
				else
				{
					System.out.println("Invalid Sequence number...contnuing");
					continue;
				}
			}catch (SocketTimeoutException execp)
			{
				if (counter<5)
				{
					/* Continuing to send packets again until 4th count 
					 * doubling the timer value everytime*/
					defTimerForDataVectorPackets = defTimerForDataVectorPackets*2;
					continue;
				}
				else
				{
					System.out.printf("Client socket timeout! Exception message at DACK: %s",execp.getMessage());
					System.exit(0);
				}
			}
		}
	}
	
	private static int extractPacketType(byte[] rcvData)
	{
		byte[] packetTypeArr = new byte[1];
		packetTypeArr = Arrays.copyOfRange(rcvData, 0, 1);
		int packetType = (int) packetTypeArr[0];
		return packetType;
	}
	private static int numOfDataVectors(LineNumberReader lnr) throws IOException
	{
		int linenumber = 0;
		while(lnr.readLine() != null)
		{
			linenumber++;
		}
		return linenumber;	
	}
	
	private static float[][] rawDataArray(Scanner input, int numOfDataVectors)
	{
				
		//Logic to read and create 2D array from data file
		int row=0;
		int col;
		float[][] dataArray = new float[numOfDataVectors][2];
		
		while(input.hasNextLine())
		{
			for(col = 0; col <2; col++)
			{
				/* Using Pattern and Matcher class to get desired array elements */
				Pattern P = Pattern.compile("-?\\d+(\\.\\d+)?");
				Matcher M = P.matcher(input.next());
				while(M.find())
				{
					dataArray[row][col] = Float.parseFloat(M.group());
			   }
			}
			row++;
		}	
		return dataArray;
	}
	
	private static byte[] IntToByteArray( int data )
	{
		byte[] result = new byte[4];

		result[0] = (byte) ((data >> 24 ) & 0XFF);
		result[1] = (byte) ((data >> 16 ) & 0XFF);
		result[2] = (byte) ((data >> 8 ) & 0XFF);
		result[3] = (byte) (data & 0xFF);
		
		return result;
	}
	private static byte[] packetType(int hexNum)
	{
		byte[] thePacketType = new byte[1];
		thePacketType[0] = (byte)hexNum; 
		return thePacketType;
	}
	
	private static byte[] twoBytePacket(int num)
	{
		byte [] seqNumPacket = new byte[2];
		seqNumPacket[0] = (byte) ((num >>8)& 0xFF); 
		seqNumPacket[1] = (byte) (num  & 0XFF);
	//	printBytes(seqNumPacket);
		return seqNumPacket;
	}
	private static void printBytes(byte[] myByte)
	{
		for(byte b1: myByte)
		{
		String s1 = String.format("%8s", Integer.toBinaryString(b1 & 0xFF)).replace(' ', '0');
		System.out.println(s1);
		}		
	}

}

