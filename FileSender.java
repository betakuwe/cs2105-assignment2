import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.CRC32;

public class FileSender {
    private static final int WINDOW_SIZE = 1 << 12;
    private static final int PACKET_BYTES = 1000;
    private static final int CHECKSUM_BYTES = 8;
    private static final int INDEX_BYTES = 4;
    private static final long ADMIN_BYTES = 22;
    private static final long DATA_BYTES = PACKET_BYTES - CHECKSUM_BYTES - INDEX_BYTES;
    private static final int FILENAME_LENGTH_BYTES = 2;
    private static final long TIME_OUT = 40;
    private static final int ACK_BYTES = 12;

    private String srcFileName;
    private String destFileName;

    private final InetSocketAddress inetSocketAddress;
    private final PacketWindow window;
    private DatagramSocket socket;
    private CRC32 crc;
    private Timer timer;

    private BufferedInputStream inputStream;
    private byte[] fileData;
    private ByteBuffer fileBuffer;
    private boolean canSendInOnePacket;
    private IndexedDatagramPacket packet;
    private int numPacketsRead;
    private int numPackets;

    private byte[] ackData;
    private ByteBuffer ackBuffer;


    private FileSender(String host, int port, String src, String dest) {
        inetSocketAddress = new InetSocketAddress(host, port);
        window = new PacketWindow(WINDOW_SIZE);
        this.srcFileName = src;
        this.destFileName = dest;
        crc = new CRC32();
        timer = new Timer();
        ackData = new byte[ACK_BYTES];
        ackBuffer = ByteBuffer.wrap(ackData);
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: FileSender <host> <port> <src> <dest>");
            System.exit(-1);
        }

        FileSender fileSender = new FileSender(args[0], Integer.parseInt(args[1]), args[2], args[3]);
        try {
            fileSender.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void run() throws IOException {
        initialiseConnection();
        makeAndSendFirstPacket();
        if (!canSendInOnePacket) {
            while (true) {
                receiveAck();
                while (windowIsAvailable() && !fileReadingIsFinished()) {
                    makeAndSendPacket();
                }
            }
        } else {
            receiveAck();
        }

    }

    private boolean fileReadingIsFinished() {
        return numPacketsRead == numPackets;
    }

    private boolean windowIsAvailable() {
        return !window.isFull();
    }
    private void receiveAck() {
        waitForAck();
        if (isValidAck()) {
            recordAck();
        }
    }

    private void recordAck() {
        window.ack(ackBuffer.getInt(CHECKSUM_BYTES));
    }

    private boolean isValidAck() {
        crc.reset();
        crc.update(ackData, CHECKSUM_BYTES, ACK_BYTES - CHECKSUM_BYTES);
        return ackBuffer.getLong(0) == crc.getValue();
    }

    private void waitForAck() {
        while (true) {
            try {
                socket.receive(new DatagramPacket(ackData, ackData.length));
                return;
            } catch (IOException e) {
                continue;
            }
        }
    }

    private void makeAndSendPacket() throws IOException {
        reinitialiseBuffer();
        inputStream.read(fileData, CHECKSUM_BYTES + INDEX_BYTES, (int) DATA_BYTES);
        fillPacketAndSend();
    }

    private void fillPacketAndSend() throws IOException {
        numPacketsRead = IndexedDatagramPacket.getNextIndex();
        fileBuffer.putInt(CHECKSUM_BYTES, numPacketsRead);
        crc.reset();
        crc.update(fileData, CHECKSUM_BYTES, PACKET_BYTES - CHECKSUM_BYTES);
        fileBuffer.putLong(0, crc.getValue());
        packet = new IndexedDatagramPacket(new DatagramPacket(fileData, fileData.length, inetSocketAddress));
        window.add(packet);
        timer.scheduleAtFixedRate(new SendPacketTask(packet), 0, TIME_OUT);
    }

    private void makeAndSendFirstPacket() throws IOException {
        reinitialiseBuffer();
        File file = new File(srcFileName);
        inputStream = new BufferedInputStream(new FileInputStream(file));

        // Making first packet. checksum 8, index 4, numPackets 4, lastpktdatalength 4,  dest length 2, dest, data
        byte[] fileNameBytes = destFileName.getBytes();

        // numPackets 4 pos 12
        int numPacketsPos = CHECKSUM_BYTES + INDEX_BYTES;
        long bytesWithoutFirstPacket = file.length() - PACKET_BYTES + ADMIN_BYTES + fileNameBytes.length;
        numPackets = bytesWithoutFirstPacket < 0 ? 0 : (int) Math.ceil((double) ((bytesWithoutFirstPacket) / DATA_BYTES)) + 1;
        canSendInOnePacket = (numPackets == 0);
        fileBuffer.putInt(numPacketsPos, numPackets);

        // lastPacketDataLength 4 pos 16
        int lastPacketDataLengthPos = numPacketsPos + Integer.BYTES;
        int lastPacketDataLength = canSendInOnePacket ? (int) file.length() : (int) (bytesWithoutFirstPacket % DATA_BYTES);
        fileBuffer.putInt(lastPacketDataLengthPos, lastPacketDataLength);

        // fileNameBytes 2
        int fileNameLengthPos = lastPacketDataLengthPos + Integer.BYTES;
        fileBuffer.putShort(fileNameLengthPos, (short) fileNameBytes.length);

        // fileName
        int fileNamePos = fileNameLengthPos + FILENAME_LENGTH_BYTES;
        System.arraycopy(fileNameBytes, 0, fileData, fileNamePos, fileNameBytes.length);

        int dataPos = fileNamePos + fileNameBytes.length;

        inputStream.read(fileData, dataPos, PACKET_BYTES - dataPos);

        fillPacketAndSend();
    }

    private void reinitialiseBuffer() {
        fileData = new byte[PACKET_BYTES];
        fileBuffer = ByteBuffer.wrap(fileData);
    }

    private void initialiseConnection() throws SocketException {
        socket = new DatagramSocket();
        socket.setSendBufferSize(Integer.MAX_VALUE);
        socket.setReceiveBufferSize(Integer.MAX_VALUE);
    }

    class SendPacketTask extends TimerTask {
        private IndexedDatagramPacket indexedPacket;

        public SendPacketTask(IndexedDatagramPacket indexedPacket) {
            this.indexedPacket = indexedPacket;
        }

        @Override
        public void run() {
            if (!window.isAcked(indexedPacket.getIndex())) {
                try {
                    socket.send(indexedPacket.getDatagramPacket());
                } catch (IOException e) {
                    super.cancel();
                }
            } else {
                super.cancel();
            }
        }
    }
}

class PacketWindow {
    private IndexedDatagramPacket[] packets;
    private int left;
    private int right;
    private boolean[] acks;
    private int lastConsecutiveAcked;

    public synchronized void add(IndexedDatagramPacket packet) {
        acks[right] = false;
        packets[right] = packet;
        right = (right + 1) % acks.length;
    }

    public synchronized void ack(int packetIndex) {
        int headPacketIndex = packets[left].getIndex();
        if (packetIndex == headPacketIndex) {
            acks[left] = true;
            while (!isEmpty() && acks[left]) {
                lastConsecutiveAcked = packets[left].getIndex();
                packets[left] = null; // clear references to unused packets
                left = (left + 1) % acks.length;
            }
            return;
        }
        int diff = packetIndex - headPacketIndex;
        if (diff > 0 && diff < acks.length) {
            acks[(diff + left) % acks.length] = true;
        }
    }

    public synchronized boolean isAcked(int packetIndex) {
        if (packetIndex <= lastConsecutiveAcked) {
            return true;
        } else if (!isEmpty()) {
            int diff = packetIndex - packets[left].getIndex();
            return diff < 0 || (diff < size() && acks[(left + diff) % acks.length]);
        } else {
            return false;
        }
    }

    public synchronized boolean isFull() {
        return (right + 1) % acks.length == left;
    }

    public synchronized boolean isEmpty() {
        return left == right;
    }

    public synchronized int size() {
        return right < left ? acks.length + right - left : right - left;
    }

    public PacketWindow(int windowSize) {
        packets = new IndexedDatagramPacket[windowSize + 1];
        acks = new boolean[packets.length];
        left = right = 0;
        lastConsecutiveAcked = -1;
    }
}

class IndexedDatagramPacket {
    private static int indices = 0;
    private DatagramPacket datagramPacket;
    private int index;

    public DatagramPacket getDatagramPacket() {
        return datagramPacket;
    }

    public int getIndex() {
        return index;
    }

    public IndexedDatagramPacket(DatagramPacket datagramPacket) {
        this.datagramPacket = datagramPacket;
        this.index = indices++;
    }

    public static int getNextIndex() {
        return indices;
    }
}
