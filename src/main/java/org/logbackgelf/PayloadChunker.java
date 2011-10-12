package org.logbackgelf;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Responsible for converting a Gelf Payload into chunks if need be.
 *
 * A Payload is the raw Gzipped Gelf JSON message.
 * A Chunk is comprised of a Gelf Chunk header as well as a portion of the payload
 */
public class PayloadChunker {

    private final int payloadThreshold;
    public final static byte[] CHUNKED_GELF_ID = new byte[]{0x1e, 0x0f};
    public final static int MESSAGE_ID_LENGTH = 32;

    public PayloadChunker(int payloadThreshold) {

        this.payloadThreshold = payloadThreshold;
    }

    public List<byte[]> go(byte[] payload) {

        return createChunks(createMessageId(), splitPayload(payload));
    }

    /**
     *
     * @param messageId
     * @param subPayloads
     * @return
     */
    private List<byte[]> createChunks(byte[] messageId, List<byte[]> subPayloads) {
        List<byte[]> chunks = new ArrayList<byte[]>();

        byte seqNum = 0;

        for(byte[] subPayload : subPayloads) {

            chunks.add(createChunk(messageId, seqNum++, (byte) subPayloads.size(), subPayload));
        }

        return chunks;
    }

    /**
     * Creates a message id that should be unique on every call. The message ID needs to be unique for every message. If
     * a message is chunked, then each chunk in a message needs the same message ID.
     *
     * @return unique message ID
     */
    private byte[] createMessageId() {
        try {
            // Uniqueness is guaranteed by combining the hostname and the current nano second, hashing the result, and
            // selecting the first 32 bytes of the result
            String hostname = InetAddress.getLocalHost().getHostName();
            String timestamp = String.valueOf(System.nanoTime());

            byte[] digestString = (hostname + timestamp).getBytes();

            try {
                MessageDigest digest = MessageDigest.getInstance("MD5");

                return Arrays.copyOf(digest.digest(digestString), MESSAGE_ID_LENGTH);
            } catch (NoSuchAlgorithmException e) {

                throw new IllegalStateException("Could not get handle on MD5 Digest for creating chunk message ID", e);
            }
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Cannot find hostname for creating chunk message ID", e);
        }
    }

    /**
     * Concatenates everything into a header and then appends the message content
     */
    private byte[] createChunk(byte[] messageId, byte seqNum, byte numChunks, byte[] chunk) {
        return  concatArrays(concatArrays(concatArrays(CHUNKED_GELF_ID, messageId), new byte[]{0x00, seqNum, 0x00, numChunks}), chunk);
    }

    /**
     * Combines and flattens two arrays (non-desctructive)
     *
     * @param array1 The first array
     * @param array2 The array to append to the end of the first
     * @return Array1 + array2
     */
    private byte[] concatArrays(byte[] array1, byte[] array2) {
        byte[] finalArray = Arrays.copyOf(array1, array2.length + array1.length);
        System.arraycopy(array2, 0, finalArray, array1.length, array2.length);
        return finalArray;
    }

    /**
     * Splits the payload data into chunks
     *
     * @param payload The full payload
     * @return A list of chunks which when added together, make up the full payload
     */
    private List<byte[]> splitPayload(byte[] payload) {
        List<byte[]> subPayloads = new ArrayList<byte[]>();

        int payloadLength = payload.length;
        int numFullSubs = payloadLength / payloadThreshold;
        int lastSubLength = payloadLength % payloadThreshold;

        for (int subPayload=0; subPayload < numFullSubs; subPayload++) {
            subPayloads.add(extractSubPayload(payload, subPayload));
        }

        if (lastSubLength > 0) {
            subPayloads.add(extractSubPayload(payload, numFullSubs));
        }

        return subPayloads;
    }

    /**
     * Extracts a chunk of bytes from another byte array based on a chunkNumber/Position
     *
     * @param payload The main array
     * @param subPaylod The chunk number to extract.
     * @return The extracted chunk
     */
    private byte[] extractSubPayload(byte[] payload, int subPaylod) {
        return Arrays.copyOfRange(payload, subPaylod * payloadThreshold, (subPaylod + 1) * payloadThreshold);
    }
}
