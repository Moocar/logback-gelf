package logbackgelf;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.logbackgelf.PayloadChunker;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ChunkTest {

    private PayloadChunker createsPackets;
    private static final int CHUNKED_GELF_ID_LENGTH = 2;
    private static final int HEADER_LENGTH = CHUNKED_GELF_ID_LENGTH + PayloadChunker.MESSAGE_ID_LENGTH + 4;

    @Before
    public void setup() {

    }

    @Test
    public void test1ByteMoreThanThreshold() {
        int byteThreshold = 3;
        createsPackets = new PayloadChunker(byteThreshold);
        byte[] data = new byte[]{1,2,3,4,5};

        List<byte[]> packets = createsPackets.go(data);

        assertEquals(2, packets.size());
        byte[] firstPacket = packets.get(0);
        assertTrue(3 != firstPacket.length);
        assertEquals(3 + HEADER_LENGTH, firstPacket.length);
        assertArrayEquals(PayloadChunker.CHUNKED_GELF_ID, Arrays.copyOfRange(firstPacket, 0, CHUNKED_GELF_ID_LENGTH));
        //System.out.println(Arrays.toString(firstPacket));
        int count = 0;
        for(byte[] packet : packets) {
            assertEquals(count, getSeqNumber(packets, count));
            assertEquals(2, getNumChunks(packet));
            count++;
        }

    }

    private int getNumChunks(byte[] packet) {
        return packet[2 + PayloadChunker.MESSAGE_ID_LENGTH + 3];
    }

    private int getSeqNumber(List<byte[]> packets, int packetNum) {
        return packets.get(packetNum)[2 + PayloadChunker.MESSAGE_ID_LENGTH + 1];
    }

    @Test
    public void testMessageIdsDifferent() {
        int byteThreshold = 3;
        createsPackets = new PayloadChunker(byteThreshold);

        byte[] data1 = new byte[]{1,2,3,4,5,6};
        byte[] data2 = new byte[]{1,2,3,4,5,6};

        List<byte[]> packets1 = createsPackets.go(data1);
        List<byte[]> packets2 = createsPackets.go(data2);

        byte[] messageId1 = Arrays.copyOfRange(packets1.get(0), 2, 10);
        byte[] messageId2 = Arrays.copyOfRange(packets2.get(0), 2, 10);

        assertFalse(Arrays.equals(messageId1, messageId2));
    }

    @Test
    public void testThreeChunks() {
        int byteThreshold = 3;
        createsPackets = new PayloadChunker(byteThreshold);
        byte[] data = new byte[]{1,2,3,4,5,6,7,8,9};

        List<byte[]> packets = createsPackets.go(data);

        assertEquals(3, packets.size());
        for(byte[] packet : packets) {
            assertEquals(3 + HEADER_LENGTH, packet.length);
        }
    }
}
