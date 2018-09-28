import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.zip.CRC32;

public class FileSender {
    private static final String DOT_SLASH = "./";
    private static final int BYTES_OF_PACKET = 1000;
    private static final int BYTES_OF_PACKET_INDEX = 4;
    private static final int BYTES_OF_CHECKSUM = 8;
    private static final int BYTES_WITHOUT_DATA = 12;
    private static final int BYTES_OF_DATA = 988;
    private static final int BYTES_WITHOUT_CHECKSUM = 992;
    

    private String host;
    private int port;
    private String src;
    private String dest;

    private InetSocketAddress addr;
    private DatagramSocket socket;
    private DatagramPacket packet;
    private CRC32 crc32 = new CRC32();
    
    private int numPackets;
    private int readyPacketIndex = 0;

    private byte[] packetData;
    private byte[][] fileData;
    private byte[] byteBuffers;
    
    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: FileSender <host> <port> <src> <dest>");
            System.exit(-1);
        }
        
        FileSender fileSender = new FileSender(args[0], Integer.parseInt(args[1]), args[2], args[3]);
        try {
            fileSender.run();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private void run() throws SocketException {
        // CompletableFuture.runAsync(() -> decomposeFileToFileDataArray(new File(src)));
        try {
            decomposeFileToFileDataArray(new File(src));
        } catch (IOException e) {
            e.printStackTrace();
        }
        connectToReceiver(host, port);
        CompletableFuture.runAsync(() -> receiveAcks());
        try {
            sendFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendFile() throws IOException {
        for (int i = 0; i < numPackets; i++) {
            packet = new DatagramPacket(fileData[i], fileData[i].length, addr);
            socket.send(packet);
        }
    }

    private void receiveAcks() {

    }

    private void connectToReceiver(String host, int port) throws SocketException {
        addr = new InetSocketAddress(host, port);
        socket = new DatagramSocket();
    }

    private void decomposeFileToFileDataArray(File file) throws IOException {
        numPackets = (int) (file.length() / BYTES_OF_PACKET) + 1;
        fileData = new byte[numPackets][BYTES_OF_PACKET];
        
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        writeToFileDataArray(bis);
    }

    private void writeToFileDataArray(BufferedInputStream bufferedInputStream) throws IOException {
        for (int i = 0; i < numPackets; i++) {
            bufferedInputStream.read(fileData[i], BYTES_WITHOUT_DATA, BYTES_OF_DATA);
            ByteBuffer byteBuffer = ByteBuffer.wrap(fileData[i]);
            byteBuffer.putLong(0);
            byteBuffer.putInt(i);
            byteBuffer.rewind();
            crc32.reset();
            crc32.update(fileData[i], BYTES_OF_CHECKSUM, BYTES_WITHOUT_CHECKSUM);
            byteBuffer.putLong(crc32.getValue());
            System.out.println("Sent CRC:" + crc32.getValue() + " Contents:" + bytesToHex(fileData[i]));
            System.out.println();
        }
    }

    private FileSender(String host, int port, String src, String dest) {
        this.host = host;
        this.port = port;
        this.src = DOT_SLASH + src;
        this.dest = DOT_SLASH + dest;
    }
    
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
}
