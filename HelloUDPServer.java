package proj3;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.BiConsumer;

public class HelloUDPServer {
	
	static HashMap<byte[], ArrayList<byte[]>> clients; 	//clients and their data
	static HashMap<byte[], Long> clientsTimeout;		//clients and their start times
	static int convoIDInt = 1;							
	static ArrayList<byte[]> clientData;				
	static byte[] convoID = new byte[4];
	static int receivedPacketSize;
	static ArrayList<byte[]> clientsToAdd;				//list of clients to add to clients map
	
	public static void main(String args[]) {
		try {
			
			//Set up
			DatagramSocket dsock = new DatagramSocket(Integer.parseInt(args[0]));
			clients = new HashMap<byte[], ArrayList<byte[]>>();
			clientsTimeout = new HashMap<byte[], Long>();
			byte[] receivedData = new byte[1024];
			clientsToAdd = new ArrayList<byte[]>();
			DatagramPacket packet = new DatagramPacket(receivedData, receivedData.length);
			
			//continuously
			while (true) {
				
				packet.setLength(1024);
				dsock.receive(packet); 					//receive packet
				receivedPacketSize = packet.getLength();
				receivedData = packet.getData();		//get the data
				
				byte[] msgNum = {(byte) receivedData[0], (byte)receivedData[1]};
				byte[] msg1Check = {(byte)0, (byte)1};
				byte[] msg2Check = {(byte)0, (byte)2};
				byte[] msg3Check = {(byte)0, (byte)3};
				
				if (!(msgNum[0] == msg1Check[0] && msgNum[1] == msg1Check[1])) { //get the convoID if not a new convo
					convoID[0] = receivedData[2];
					convoID[1] = receivedData[3];
					convoID[2] = receivedData[4];
					convoID[3] = receivedData[5];
				}
				
				if (msgNum[0] == msg1Check[0] && msgNum[1] == msg1Check[1]) { //if msg #1
					byte[] sendData = msg1Response(receivedData);	//analyze data
					clientData = new ArrayList<byte[]>();			
					clientData.add(convoID);
					clients.put(convoID, clientData);				//put the client in the map
					long time = System.currentTimeMillis();
					clientsTimeout.put(convoID, time);				//map the clients start time
					packet.setData(sendData);
					packet.setLength(sendData.length);
					dsock.send(packet);								//send the server message
				} else if (msgNum[0] == msg2Check[0] && msgNum[1] == msg2Check[1] && clients.containsKey(convoID)) { //if msg #2
					byte[] sendData = msg2Response(receivedData);	//analyze data
					packet.setData(sendData);
					packet.setLength(sendData.length);
					dsock.send(packet);								//send the server message
				} else if (msgNum[0] == msg3Check[0] && msgNum[1] == msg3Check[1] && clients.containsKey(convoID)) { //if msg #3
					byte[] sendData = msg3Response(receivedData); //analyze data
					packet.setData(sendData);
					packet.setLength(sendData.length);
					dsock.send(packet);								//send the server message
					clients.remove(convoID);						//remove the client
				} else {					//If error message
					byte[] msgNumError = {(byte)0, (byte)5};
					String msg = "Error: Unknown message number/client convo ID";
					byte[] msgBytes = msg.getBytes();
					byte[] sendData = new byte[1024];
					System.arraycopy(msgNumError, 0, sendData, 0, msgNumError.length);
					System.arraycopy(msgBytes, 0, sendData, msgNumError.length, msgBytes.length);
					packet.setData(sendData);
					packet.setLength(sendData.length);
					dsock.send(packet); 	//send error message
				}
				
				BiConsumer<byte[], Long> action = new MyBiConsumer();
				clientsTimeout.forEach(action);						//test after every loop for client timeout
				
			}
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	
	//analyze msg1 data
	public static byte[] msg1Response(byte[] receivedData) {
		
		//print packet info
		System.out.print("Message number: " + receivedData[0]);
		System.out.println(receivedData[1]);
		System.out.println("Client message: " + new String(receivedData, 2, receivedData.length - 2));
		
		//create server msg1 response
		byte[] msgNum = {(byte)receivedData[0], (byte)receivedData[1]};
		convoID = ByteBuffer.allocate(4).putInt(convoIDInt).array();
		convoIDInt++;
		String serverMsgString = "hello, i am kerry (012034529)";
		byte[] serverMsg = serverMsgString.getBytes();
		byte[] sendData = new byte[1024];
		System.arraycopy(msgNum, 0, sendData, 0, msgNum.length);
		System.arraycopy(convoID, 0, sendData, msgNum.length, convoID.length);
		System.arraycopy(serverMsg, 0, sendData, msgNum.length + convoID.length, serverMsg.length);
		return sendData;
	}
	
	
	
	//analyze msg2 data
	public static byte[] msg2Response(byte[] receivedData) {
		
		//print packet info
		System.out.print("Message number: " + receivedData[0]);
		System.out.println(receivedData[1]);
		byte[] convoID2 = {(byte)(receivedData[2] & 0xff), (byte)(receivedData[3] & 0xff), (byte)(receivedData[4] & 0xff), (byte)(receivedData[5] & 0xff)};
		BigInteger bIConvo = new BigInteger(convoID2);
		System.out.println("Conversation ID: " + bIConvo);
		int offsetInt = (receivedData[6] & 0xff) << 24 | (receivedData[7] & 0xff) << 16 | (receivedData[8] & 0xff) << 8 | (receivedData[9] & 0xff);
		System.out.println("Offset amount: " + offsetInt);
		
		//save the client data
		byte[] dataArray = new byte[100];
		int j = 0;
		if ((offsetInt + receivedPacketSize - 10) % 100 == 0) { //for all 100B data
			for (int i = 10; i < receivedPacketSize; i++) {
				dataArray[j] = receivedData[i];
				j++;
			}
			clients.get(convoID).add(offsetInt/100, dataArray);	//put the data in clients map at the right offset
		} else {												//for < 100B data
			dataArray = new byte[(receivedPacketSize - 10)];
			for (int i = 10; i < receivedPacketSize; i++) {
				dataArray[j] = receivedData[i];
				j++;
			}
			clients.get(convoID).remove(clients.get(convoID).size() - 1); 
			clients.get(convoID).add((offsetInt/100), dataArray);//put the data in clients map at the right offset
		}
		
		//create server msg2 response
		byte[] msgNum = {(byte)receivedData[0], (byte)receivedData[1]};
		byte[] convID = {(byte)receivedData[2], (byte)receivedData[3], (byte)receivedData[4], (byte)receivedData[5]};
		byte[] offsetACK = {(byte)receivedData[6], (byte)receivedData[7], (byte)receivedData[8], (byte)receivedData[9]};
		byte[] sendData = new byte[1024];
		System.arraycopy(msgNum, 0, sendData, 0, msgNum.length);
		System.arraycopy(convID, 0, sendData, msgNum.length, convID.length);
		System.arraycopy(offsetACK, 0, sendData, msgNum.length + convID.length, offsetACK.length);
		return sendData;
	}
	
	
	
	//analyze msg3 data
	public static byte[] msg3Response(byte[] receivedData) {
		
		//print packet info
		System.out.print("Message number: " + receivedData[0]);
		System.out.println(receivedData[1]);
		byte[] convoID3 = {(byte)(receivedData[2] & 0xff), (byte)(receivedData[3] & 0xff), (byte)(receivedData[4] & 0xff), (byte)(receivedData[5] & 0xff)};
		BigInteger bIConvo = new BigInteger(convoID3);
		System.out.println("Conversation ID: " + bIConvo);
		
		//Find the checksum
		byte[] clientChecksum = new byte[32];
		for (int i = 0; i < 32; i++) {
			clientChecksum[i] = receivedData[i+6];
		}
		byte[] serverChecksum = new byte[32];
		int bufSize = 0;
		for (int i = 0; i < clients.get(convoID).size(); i++) { //get the needed buffer size
			bufSize += clients.get(convoID).get(i).length;
		}
		ByteBuffer buf = ByteBuffer.allocate(bufSize);			//allocate the needed buffer size
		for (int i = 0; i < clients.get(convoID).size(); i++) {
			buf.put(clients.get(convoID).get(i));				//add the data to the buffer
		}
		byte[] checksumData = buf.array();
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			serverChecksum = md.digest(checksumData);			//Hash the data
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//create server msg3 response
		byte[] msgNum = {(byte)receivedData[0], (byte)receivedData[1]};
		byte[] convID = {(byte)receivedData[2], (byte)receivedData[3], (byte)receivedData[4], (byte)receivedData[5]};
		byte[] passFail = new byte[1];
		if (Arrays.equals(serverChecksum, clientChecksum)) { //check for checksum equality
			passFail[0] = 0;
		} else {
			passFail[0] = 1;
		}
		byte[] sendData = new byte[1024];
		System.arraycopy(msgNum, 0, sendData, 0, msgNum.length);
		System.arraycopy(convID, 0, sendData, msgNum.length, convID.length);
		System.arraycopy(passFail, 0, sendData, msgNum.length + convID.length, passFail.length);
		System.arraycopy(serverChecksum, 0, sendData, msgNum.length + convID.length + passFail.length, serverChecksum.length);
		for (byte b : serverChecksum) {
			String s = String.format("%02X", b);
			System.out.print(s);
		}
		return sendData;
	}
}



//for removing clients from the maps who have timed-out
class MyBiConsumer implements BiConsumer<byte[], Long> {

	public void accept(byte[] b, Long l) {
		if ((System.currentTimeMillis() - l) > 10000) {
			HelloUDPServer.clients.remove(b);
			HelloUDPServer.clientsTimeout.remove(b);
		}
	}
}
