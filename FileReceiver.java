import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.TreeSet;
import java.util.zip.CRC32;

public class FileReceiver {
    private static final int PACKET_BYTES = 1000;
    private static final int MAX_DATA_SEGMENTS = 1 << 12;
    private static final int ACK_BYTES = 12;
    private static final int INDEX_POS = 8;
    private static final int NUM_PACKETS_POS = 12;
    private static final int LAST_PACKET_DATA_LENGTH_POS = NUM_PACKETS_POS + 4;
    private static final int FILENAME_LENGTH_POS = LAST_PACKET_DATA_LENGTH_POS + 4;
    private static final int FILENAME_POS = FILENAME_LENGTH_POS + 2;
    private static final int DATA_POS = 12;
    private static final int DATA_BYTES = PACKET_BYTES - DATA_POS;

    private final int port;

    private DatagramSocket socket;
    private SocketAddress address;
    private boolean isAddressSet;

    private TreeSet<DataSegment> tree;
    private int expectedDataIndex;
    private DataSegment segment;
    private byte[] data;

    private byte[] ackData;
    private ByteBuffer byteBuffer;
    private CRC32 crc;

    private int numPackets;
    private int lastPacketDataLength;
    private BufferedOutputStream outputStream;

    public FileReceiver(int port) {
        this.port = port;
        tree = new TreeSet<>();
        ackData = new byte[ACK_BYTES];
        byteBuffer = ByteBuffer.wrap(ackData);
        crc = new CRC32();
        isAddressSet = false;
        expectedDataIndex = 0;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: HardFileReceiver <port>");
            System.exit(-1);
        }

        FileReceiver fileReceiver = new FileReceiver(Integer.parseInt(args[0]));
        try {
            fileReceiver.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void run() throws IOException {
        socket = new DatagramSocket(port);
        socket.setReceiveBufferSize(Integer.MAX_VALUE);
        socket.setSendBufferSize(Integer.MAX_VALUE);
        while (true) {
            receivePacket();
            int diff = segment.getIndex() - expectedDataIndex;
            if (diff < MAX_DATA_SEGMENTS && isValidPacket()) {
                sendAck();
                if (0 <= diff) {
                    if (segment.getIndex() == expectedDataIndex) {
                        writeBasePacket();
                        while (treeBaseIsExpected()) {
                            writeSubsequentPackets();
                        }
                    } else {
                        storeData();
                    }
                }

            }
        }
    }

    private void writeSubsequentPackets() throws IOException {
        data = tree.pollFirst().getData();
        if (expectedDataIndex == numPackets) {
            writeLastPacket();
        } else {
            writeRemainingPacket();
        }
        expectedDataIndex++;
    }

    private boolean treeBaseIsExpected() {
        return !tree.isEmpty() && tree.first().getIndex() == expectedDataIndex;
    }

    private void writeBasePacket() throws IOException {
        if (expectedDataIndex == 0) {
            writeFirstPacket();
            expectedDataIndex++;
            return;
        } else if (expectedDataIndex == numPackets) {
            writeLastPacket();
            expectedDataIndex++;
        } else {
            writeRemainingPacket();
            expectedDataIndex++;
        }
    }

    private void writeLastPacket() throws IOException {
        outputStream.write(data, DATA_POS, lastPacketDataLength);
        outputStream.flush();
    }

    private void writeRemainingPacket() throws IOException {
        outputStream.write(data, DATA_POS, DATA_BYTES);
        outputStream.flush();
    }

    private void writeFirstPacket() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        numPackets = byteBuffer.getInt(NUM_PACKETS_POS);
        lastPacketDataLength = byteBuffer.getInt(LAST_PACKET_DATA_LENGTH_POS);
        short fileNameLength = byteBuffer.getShort(FILENAME_LENGTH_POS);
        String fileName = new String(data, FILENAME_POS, fileNameLength);
        outputStream = new BufferedOutputStream(new FileOutputStream(fileName));
        outputStream.write(data, FILENAME_POS + fileNameLength,
            numPackets == 0 ? lastPacketDataLength : PACKET_BYTES - FILENAME_POS - fileNameLength);
        outputStream.flush();
    }

    private void sendAck() {
        byteBuffer.putInt(INDEX_POS, segment.getIndex());
        crc.reset();
        crc.update(ackData, INDEX_POS, ACK_BYTES - INDEX_POS);
        byteBuffer.putLong(0, crc.getValue());
        try {
            socket.send(new DatagramPacket(ackData, ACK_BYTES, address));
        } catch (IOException e) {
            return;
        }
    }

    private void storeData() {
        tree.add(segment);
    }

    private boolean isValidPacket() {
        return segment.isValidDataSegment();
    }

    private void receivePacket() {
        data = new byte[PACKET_BYTES];
        DatagramPacket packet = new DatagramPacket(data, PACKET_BYTES);
        packet.setLength(PACKET_BYTES);
        while (true) {
            try {
                socket.receive(packet);
                break;
            } catch (IOException e) {
                continue;
            }
        }
        if (!isAddressSet) address = packet.getSocketAddress();
        segment = new DataSegment(data);
    }
}

class DataSegment implements Comparable<DataSegment> {
    private static final CRC32 CRC_32 = new CRC32();
    private static final int INDEX_POS = Long.BYTES;
    private static final int NON_CHECKSUM_POS = Long.BYTES;
    private static final int NON_CHECKSUM_BYTES = 1000 - NON_CHECKSUM_POS;

    private final int index;
    private final byte[] data;

    public boolean isValidDataSegment() {
        CRC_32.reset();
        CRC_32.update(data, NON_CHECKSUM_POS, NON_CHECKSUM_BYTES);
        return ByteBuffer.wrap(data).getLong(0) == CRC_32.getValue();
    }

    public int getIndex() {
        return index;
    }

    public byte[] getData() {
        return data;
    }

    public DataSegment(byte[] data) {
        this.data = data;
        index = ByteBuffer.wrap(data).getInt(INDEX_POS);
    }

    @Override
    public int compareTo(DataSegment o) {
        return index - o.index;
    }
}
