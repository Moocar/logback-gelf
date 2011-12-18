package me.moocar.logbackgelf;

import org.junit.Before;
import org.junit.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ChunkTest {

    private PayloadChunker createsPackets;
    private static final byte[] CHUNKED_GELF_ID = new byte[]{0x1e, 0x0f};
    private static final int CHUNKED_GELF_ID_LENGTH = CHUNKED_GELF_ID.length;
    public final static int SEQ_NUM_LENGTH = 2;
    public final static int SEQ_LENGTH = 2;
    public final static int MESSAGE_ID_LENGTH = 8;
    private static final int HEADER_LENGTH = CHUNKED_GELF_ID_LENGTH + MESSAGE_ID_LENGTH + SEQ_NUM_LENGTH + SEQ_LENGTH;
    private static final int DEFAULT_THRESHOLD = 3;
    private static final int MAX_CHUNKS = 127;

    @Before
    public void setup() throws NoSuchAlgorithmException {
        createsPackets = new PayloadChunker(DEFAULT_THRESHOLD, MAX_CHUNKS, new MessageIdProvider(MESSAGE_ID_LENGTH, MessageDigest.getInstance("MD5"), "localhost"), new ChunkFactory(CHUNKED_GELF_ID, true));
    }

    @Test
    public void test1ByteMoreThanThreshold() {
        List<byte[]> packets = go(new byte[]{1,2,3,4,5});

        assertEquals(2, packets.size());

        byte[] firstPacket = packets.get(0);
        assertTrue(DEFAULT_THRESHOLD != firstPacket.length);
        assertEquals(DEFAULT_THRESHOLD + HEADER_LENGTH, firstPacket.length);

        assertArrayEquals(CHUNKED_GELF_ID, Arrays.copyOfRange(firstPacket, 0, CHUNKED_GELF_ID_LENGTH));

        int count = 0;
        for(byte[] packet : packets) {
            assertEquals(count, getSeqNumber(packets, count));
            assertEquals(2, getNumChunks(packet));
            count++;
        }

    }

    @Test
    public void testThreeChunks() {
        List<byte[]> packets = go(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9});

        assertEquals(3, packets.size());

        for(byte[] packet : packets) {
            assertEquals(DEFAULT_THRESHOLD + HEADER_LENGTH, packet.length);
        }
    }

    @Test
    public void testMessageIdsDifferent() {
        List<byte[]> packets1 = go(new byte[]{1,2,3,4,5,6});
        List<byte[]> packets2 = go(new byte[]{1, 2, 3, 4, 5, 6});

        byte[] messageId1 = Arrays.copyOfRange(packets1.get(0), 2, 10);
        byte[] messageId2 = Arrays.copyOfRange(packets2.get(0), 2, 10);

        assertFalse(Arrays.equals(messageId1, messageId2));
    }

    @Test
    public void shouldCutoffAfterMaxChunks() {
        byte[] payload = createMassivePayload();
        List<byte[]> packets = go(payload);

        assertEquals(MAX_CHUNKS, packets.size());
    }

    private byte[] createMassivePayload() {
        byte[] massiveArray = new byte[(MAX_CHUNKS + 2) * DEFAULT_THRESHOLD];
        Arrays.fill(massiveArray, (byte)9);
        return massiveArray;
    }

    private List<byte[]> go(byte[] bytes) {
        return createsPackets.chunkIt(bytes);
    }

    private int getNumChunks(byte[] packet) {
        return packet[CHUNKED_GELF_ID.length + MESSAGE_ID_LENGTH + SEQ_NUM_LENGTH + SEQ_LENGTH - 1];
    }

    private int getSeqNumber(List<byte[]> packets, int packetNum) {
        return packets.get(packetNum)[CHUNKED_GELF_ID.length + MESSAGE_ID_LENGTH + SEQ_NUM_LENGTH - 1];
    }
}
