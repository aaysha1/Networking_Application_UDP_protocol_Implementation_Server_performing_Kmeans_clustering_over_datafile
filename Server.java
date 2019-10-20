package Project;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Server {

	public static void main(String[] args) throws Exception
	{
		final int DATA_PACKET = 0x00;
		final int INITIAL_SEQ_NUM = 0;
		int numOfTries = 0;
		DatagramSocket myServer = new DatagramSocket(1234);
		byte[] rcvData = new byte[405];
		int seqNum = INITIAL_SEQ_NUM;
		ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();
		DatagramPacket rcvPacket = new DatagramPacket(rcvData,rcvData.length);
		byte[] newbuf = new byte[rcvPacket.getLength()-5];
		System.out.println("Server is receving data vectors...");
		while(true)
		{
			//Receiving DATA Packet...
			myServer.receive(rcvPacket);			 
			rcvData = rcvPacket.getData();
			byte[] seqNumByte = new byte[2];
			seqNumByte = Arrays.copyOfRange(rcvData, 1,3);
			/* Extracting Packet Type from 1 byte array */
			int dataPacketType = extractPacketType(rcvData);
			
			int rcvSeqNum = 0;
			rcvSeqNum =  (seqNumByte[0] & 0xff) << 8 | (seqNumByte[1] & 0xff);  
		
			if(dataPacketType == DATA_PACKET )
			{
				if (seqNum == rcvSeqNum)
				{
					newbuf = Arrays.copyOfRange(rcvData, 5,rcvPacket.getLength());
					byte[] ACKdataPacket = new byte[3];
					ByteArrayOutputStream AckBuffer = new ByteArrayOutputStream(); 
					AckBuffer.write(packetType(1));
					AckBuffer.write(seqNumByte);
					ACKdataPacket = AckBuffer.toByteArray(); 
					dataBuffer.write(newbuf);
					int sendPort = rcvPacket.getPort();
					InetAddress sendAddr  = rcvPacket.getAddress();
					
					DatagramPacket ACKPacket = new DatagramPacket(ACKdataPacket, ACKdataPacket.length,sendAddr,sendPort );
					try
					{
						//Sending DACK Packet with correct sequence number...
						myServer.send(ACKPacket);
						seqNum++;
						continue;	
					}catch (SocketTimeoutException execp)
					{
						System.out.printf("Client socket timeout! Exception message: %s",execp.getMessage());
						System.exit(0);
					}
				}
				else
				{
					if (numOfTries < 5)
					{
						/* Continuing to send packets again until 4th count 
						 * doubling the timer value everytime*/
						//Sending DACK Packet with previous correctly received sequence number...
						byte[] ACKdataPacket = new byte[3];
						ByteArrayOutputStream AckBuffer = new ByteArrayOutputStream(); 
						AckBuffer.write(packetType(1));
						
						byte [] oldSeqNumPacket = new byte[2];
						oldSeqNumPacket[0] = (byte) ((seqNum >>8)& 0xFF); 
						oldSeqNumPacket[1] = (byte) (seqNum  & 0XFF);
						
						AckBuffer.write(oldSeqNumPacket);
						ACKdataPacket = AckBuffer.toByteArray(); 
						int sendPort = rcvPacket.getPort();
						InetAddress sendAddr = rcvPacket.getAddress();
						
						DatagramPacket ACKPacket = new DatagramPacket(ACKdataPacket, ACKdataPacket.length,sendAddr,sendPort );
						myServer.send(ACKPacket);
						numOfTries++;
						continue;
					}
					else
					{
						/* if the sequence number is invalid even after 4 tries.. the system should exit */
						System.out.printf("Invalid Sequence number... Exiting system\n");
						myServer.close();
						System.exit(0);
					}
				}
			}
			else
			{
				// Invalid Packet Type...
				break;
			}
		}
		
		int defTimerForREQPacket = 3000;
		
		while(true)
		{
			//Receiving REQ Packet...
			byte[] REQPacket = new byte[1];
			DatagramPacket rcvREQPacket = new DatagramPacket(REQPacket,REQPacket.length);
			myServer.receive(rcvREQPacket);
			REQPacket = rcvREQPacket.getData();
			int REQPacketType = extractPacketType(REQPacket);
			if (REQPacketType == 0x02)
			{
				ByteArrayOutputStream RACKBuffer = new ByteArrayOutputStream();
				RACKBuffer.write(packetType(0x03));
				int sendPort = rcvREQPacket.getPort();
				InetAddress sendAddr  = rcvREQPacket.getAddress();
				byte[] RACKDataPacket = new byte[1];
				RACKDataPacket = RACKBuffer.toByteArray(); 
				DatagramPacket RACKPacket = new DatagramPacket(RACKDataPacket, RACKDataPacket.length,sendAddr,sendPort );
				
				try
				{
					//Sending RACK Packet...
					myServer.send(RACKPacket);
					//Starting the timer to receive any other REQ Packet in between...
					myServer.setSoTimeout(defTimerForREQPacket);
					break;
				}catch (SocketTimeoutException execp)
				{
					System.out.println("No duplicate REQ received");
					break;
				}	
			}
		}
		
		int defTimerForCACKPacket = 1000;
		int CLUScounter = 0;
		byte[] finalClusPacket = new byte[17];
		while(true)
		{
			System.out.println("Calculating Centroid by K-means Clustering Algorithm...");
			byte[] clusvector = new byte[16];
			byte[] rcvDataVectorsArr = dataBuffer.toByteArray();
			int mylength = (rcvDataVectorsArr.length)/8;

			float[][] rawDataVectorsArr= new float[mylength][2]; 
			int row=0;
			

			for(int index=0; index<rcvDataVectorsArr.length; index+=8)
			{
				
				/* Creating float 2D array, working on a single data vector at a time */
				/* First dimension of a data vector */
				byte[] FirstDimension=new byte[4];
				//Copying first four bytes
				System.arraycopy(rcvDataVectorsArr, index, FirstDimension, 0, 4);
				int num1 = 0;
				//Byte shifting and then converting it to float
				num1 = (FirstDimension[0] & 0xff) << 24 | (FirstDimension[1] & 0xff) << 16 | (FirstDimension[2] & 0xff) << 8 | (FirstDimension[3] & 0xff);
				rawDataVectorsArr[row][0] = ((float)num1)/100;

	 			
	 			/* Second dimension of a data vector */
				byte[] SecondDimension=new byte[4];
				//Copying next four bytes
				System.arraycopy(rcvDataVectorsArr, index+4, SecondDimension, 0, 4);
				//Byte shifting and then converting it to float
				int num2 = (SecondDimension[0] & 0xff) << 24 | (SecondDimension[1] & 0xff) << 16 | (SecondDimension[2] & 0xff) << 8 | (SecondDimension[3] & 0xff);
				rawDataVectorsArr[row][1]=((float)num2)/100;				
				row++;
			}
			

			int length =rawDataVectorsArr.length; //length of the float array
	
			/* Centroids of the clusters are assumed, we have to choose random number */
			float[] x = new float[length];  // all x values in x array to find range
			float[] y = new float[length];	// all y values in y array to find range
			for(int index=0; index < length; index++)
			{
				x[index] = rawDataVectorsArr[index][0];
				y[index] = rawDataVectorsArr[index][1];
			}
			
			//Range of x and y
			float xMax,xMin,yMax,yMin;
			xMax = xMin = x[0];
			yMax = yMin = y[0];
			for(int index = 0; index < length; index++)
			{
				//For x
				if(x[index]>xMax)
					xMax=x[index];
				if(x[index]<xMin)
					xMin=x[index];
				//For y
				if(y[index]>yMax)
					yMax=y[index];
				if(y[index]<yMin)
					yMin=y[index];
			}
			
			Random rand=new Random();
			float m11 =	rand.nextFloat() * (xMax-xMin) + xMin;
			float m12=	rand.nextFloat() * (yMax-yMin) + yMin;
			
			float m21=	rand.nextFloat() * (xMax-xMin) + xMin;
			float m22=	rand.nextFloat() * (yMax-yMin) + yMin;
		
			float distancebetweenM1,distanceBetweenM2,convergence,m11new,m12new,m21new,m22new;
			
			m11new= m11;
			m12new= m12;
			m21new= m21;
			m22new= m22;
			
			convergence = (float) (0.00001);
		
			distancebetweenM1=distanceBetweenM2=0;
			
	
			// Numbers of clusters to be created for K = 2 
			do
			{
				float[] centroids= clusterCreateNewCentroid(m11new,m12new,rawDataVectorsArr,m21new,m22new,rawDataVectorsArr.length);
				
				m11new= centroids[0];
				m12new= centroids[1];
				m21new= centroids[2];
				m22new= centroids[3];
								
				float m11old= centroids[4];
				float m12old= centroids[5];
				float m21old= centroids[6];
				float m22old= centroids[7];
		
				distancebetweenM1= (float) Math.sqrt((m11new-m11old)*(m11new-m11old) + (m12new-m12old)*(m12new-m12old));
				distanceBetweenM2= (float) Math.sqrt((m21new-m21old)*(m21new-m21old) + (m22new-m22old)*(m22new-m22old));
		
			}while ((distancebetweenM1+distanceBetweenM2) > convergence);
		
			if((distancebetweenM1+distanceBetweenM2)<=convergence)
			{
				//clus packet make
				float finalcentroid[] = {m11new, m12new, m21new, m22new};
				
				byte[] byteconversion = new byte[4];
				ByteArrayOutputStream clusdata = new ByteArrayOutputStream(byteconversion.length);
				for(int index=0; index<finalcentroid.length; index++)
				{
					int intconversion= (int) (finalcentroid[index]*100);
						
						
					byteconversion[0] = (byte) ((intconversion >> 24) & 0XFF);
					byteconversion[1] = (byte) ((intconversion >> 16 ) & 0XFF);
					byteconversion[2] = (byte) ((intconversion >> 8 ) & 0XFF);
					byteconversion[3] = (byte) (intconversion & 0xFF);
					
					clusdata.write(byteconversion, 0, byteconversion.length);
				
				}
				
				clusvector = clusdata.toByteArray();
			}

			byte[] clusPacketType = new byte[1];
			clusPacketType = packetType(4);
			ByteArrayOutputStream finalClusBuffer = new ByteArrayOutputStream();
			finalClusBuffer.write(clusPacketType);
			finalClusBuffer.write(clusvector);
			finalClusPacket = finalClusBuffer.toByteArray();
			
			int sendPort = rcvPacket.getPort();
			InetAddress sendAddr  = rcvPacket.getAddress();
			DatagramPacket CLUSPacket = new DatagramPacket(finalClusPacket, finalClusPacket.length,sendAddr,sendPort );
			System.out.println("Sending CLUS Packet with calculated Centroids...");
			myServer.send(CLUSPacket);

			System.out.println("Starting timer to receive CACK Packet");
			myServer.setSoTimeout(defTimerForCACKPacket);
			byte[] CACKPacket = new byte[1];
			DatagramPacket rcvCACKPacket = new DatagramPacket(CACKPacket,CACKPacket.length);
			try
			{
				myServer.receive(rcvCACKPacket);
				CLUScounter++;
				System.out.println("... Exiting System");
				myServer.close();
				System.exit(0);
			}
			catch (SocketTimeoutException execp)
			{
				if (CLUScounter<5)
				{
					/* Continuing to send packets again until 4th count 
					 * doubling the timer value everytime*/
					defTimerForCACKPacket *=2;
					continue;
				}
				else
				{
					System.out.printf("Client socket timeout! Exception message while waiting for CACK: %s",execp.getMessage());
					System.exit(0);
				}
			}
		}
		
	}
	
	public static float[] clusterCreateNewCentroid(float m11,float m12, float[][] fl,float m21,float m22,int length)
	{
		//distance between centroids and data vectors and creating two clusters
		
		float[][] cluster1=new float[length][2];
		float[][] cluster2=new float[length][2];
		int rowcluster1=0;
		int rowcluster2=0;
		
		for(int index=0;index<length;index++)
		{
			// The two dimensions of the data-vectors mapped to s,t
			float dimension1=fl[index][0];
			float dimension2=fl[index][1];
			/* Calculating distance using Math library */
			float distancefromM1= (float) Math.sqrt((m11-dimension1)*(m11-dimension1) + (m12-dimension2)*(m12-dimension2));
			float distancefromM2= (float) Math.sqrt((m21-dimension1)*(m21-dimension1) + (m22-dimension2)*(m22-dimension2));
			
			// Vector distribution in both clusters
			if(distancefromM1<distancefromM2)
			{
				cluster1[rowcluster1][0]=dimension1;
				cluster1[rowcluster1][1]=dimension2;
				rowcluster1++;
			}
			else
			{
				cluster2[rowcluster2][0]=dimension1;
				cluster2[rowcluster2][1]=dimension2;
				rowcluster2++;
			}
		}
		
		// For cluster1
		float cluster1dimension1sum,cluster1dimension2sum;
		cluster1dimension1sum=cluster1dimension2sum=0;
		for(int row=0; row<cluster1.length; row++)
		{
			cluster1dimension1sum=cluster1dimension1sum+cluster1[row][0];
			cluster1dimension2sum=cluster1dimension2sum+cluster1[row][1];
		}
		
		float mnew11=cluster1dimension1sum/rowcluster1;
		float mnew12=cluster1dimension2sum/rowcluster1;
		
		// For cluster2
		float cluster2dimension1sum,cluster2dimension2sum;
		cluster2dimension1sum=cluster2dimension2sum=0;	
		
		for(int row=0; row<cluster2.length; row++)
		{
			cluster2dimension1sum=cluster2dimension1sum+cluster2[row][0];
			cluster2dimension2sum=cluster2dimension2sum+cluster2[row][1];
		}
		
		float mnew21=cluster2dimension1sum/rowcluster2; //last 1 number is counted as we starting rowcluster from 0 
		float mnew22=cluster2dimension2sum/rowcluster2;
		float[] centroid = {mnew11,mnew12,mnew21,mnew22,m11,m12,m21,m22};
		/* Returning the new and old clusters */
		return(centroid);
		
	}
	
	/* To extract Packet type from 1st byte of received packet */
	private static int extractPacketType(byte[] rcvData)
	{
		byte[] packetTypeArr = new byte[1];
		packetTypeArr = Arrays.copyOfRange(rcvData, 0, 2);
		int packetType = (int) packetTypeArr[0];
		return packetType;
	}
	
	/* To print bytes */
	public static void printBytes(byte[] newbuf)
	{
		for(byte b1: newbuf)
		{
		String s1 = String.format("%8s", Integer.toBinaryString(b1 & 0xFF)).replace(' ', '0');
		System.out.println(s1);
		}
	}
	
	/* To make a 1 byte packet type which gets a hex num as its argument */
	private static byte[] packetType(int hexNum)
	{
		byte[] thePacketType = new byte[1];
		thePacketType[0] = (byte)hexNum; 
		return thePacketType;
	}
	
	/* To print 2D arrays if one wants to check the output of a floating point 2D Array */

	public static void print2darray(float[][] arr)
	{
		System.out.print("\n{");
		for(int row=0; row<arr.length; row++)
		{
			System.out.print("{");
			for(int col=0; col<2; col++)
			{
				System.out.print(arr[row][col]);
				if((col+1)!=2)
				{
					System.out.print(",");
				}
			}
			System.out.print("}");
			if((row+1)!=arr.length)
			{
				System.out.print(",");
			}
		}
	}
}
