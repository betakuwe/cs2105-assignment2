import java.net.*;
import java.nio.*;
import java.util.zip.*;

public class SimpleUDPReceiver {

	public static void main(String[] args) throws Exception
	{
		checkArgs(args);
		int port = Integer.parseInt(args[0]);
		DatagramSocket sk = new DatagramSocket(port);
		byte[] data = new byte[1500];
		DatagramPacket pkt = new DatagramPacket(data, data.length);
		ByteBuffer b = ByteBuffer.wrap(data);
		CRC32 crc = new CRC32();
		while(true)
		{
			pkt.setLength(data.length);
			sk.receive(pkt);
			checkPacketLength(pkt, 8);
			b.rewind();
			long chksum = b.getLong();
			crc.reset();
			crc.update(data, 8, pkt.getLength()-8);
			// Debug output
			System.out.println("Received CRC:" + crc.getValue() + " Data:" + bytesToHex(data, pkt.getLength()));
			if (crc.getValue() != chksum)
			{
				System.out.println("Pkt corrupt");
			}
			else
			{
				System.out.println("Pkt " + b.getInt());

				DatagramPacket ack = new DatagramPacket(new byte[0], 0, 0,
					pkt.getSocketAddress());
				sk.send(ack);
			}
			System.out.println();
		}
	}

	private static void checkPacketLength(DatagramPacket pkt, int lowerPacketLimit) {
		if (pkt.getLength() < lowerPacketLimit) {
			System.out.println("Pkt too short");
		}
	}

	private static void checkArgs(String[] args) {
		if (args.length != 1) {
			System.err.println("Usage: SimpleUDPReceiver <port>");
			System.exit(-1);
		}
	}

	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes, int len) {
		char[] hexChars = new char[len * 2];
		for ( int j = 0; j < len; j++ ) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
}
