import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.CompletableFuture;

public class FileSender {
    private static final String DOT_SLASH = "./";
    private static final int MAX_SIZE = 1000;

    private String host;
    private int port;
    private String src;
    private String dest;

    private InetSocketAddress addr;
    private DatagramSocket socket;
    private DatagramPacket packet;
    private int numPackets;
    private int readyPacketIndex = 0;

    private byte[] packetData;
    private byte[][] fileData;
    
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
        CompletableFuture.runAsync(() -> decomposeFile(new File(src)));

        connectToReceiver(host, port);
        CompletableFuture.runAsync(() -> receiveAcks());
        sendFile();
    }

    private void sendFile() {
    }

    private void receiveAcks() {

    }

    private void connectToReceiver(String host, int port) throws SocketException {
        addr = new InetSocketAddress(host, port);
        socket = new DatagramSocket();
    }

    private void decomposeFile(File file) {

    }

    private FileSender(String host, int port, String src, String dest) {
        this.host = host;
        this.port = port;
        this.src = DOT_SLASH + src;
        this.dest = DOT_SLASH + dest;
    }
}
