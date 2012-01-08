package me.moocar.logbackgelf;

import java.util.Arrays;

/**
 * Responsible for wrapping a subPayload in a GELF Chunk
 */
public class ChunkFactory {

    private final byte[] chunkedGelfId;
    private final boolean padSeq;

    public ChunkFactory(byte[] chunked_gelf_id, boolean padSeq) {
        chunkedGelfId = chunked_gelf_id;
        this.padSeq = padSeq;
    }

    /**
     * Concatenates everything into a GELF Chunk header and then appends the sub Payload
     * @param messageId The message ID of the message (remains same for all chunks)
     * @param seqNum The sequence number of the chunk
     * @param numChunks The number of chunks that will be sent
     * @param subPayload The actual data that will be sent in this chunk
     * @return The complete chunk that wraps the sub pay load
     */
    public byte[] create(byte[] messageId, byte seqNum, byte numChunks, byte[] subPayload) {

        return  concatArrays(concatArrays(concatArrays(chunkedGelfId, messageId), getSeqNumbers(seqNum, numChunks)), subPayload);
    }

    /**
     * Returns array1 concatenated with array2 (non-destructive)
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
     * Returns an array of sequence numbers for each chunk. Needed because apparently we need to pad this for 0.9.5??
     *
     * @param seqNum The Sequence Number
     * @param numChunks The number of chunks that will be sent
     * @return An array of sequence numbers for each chunk.
     */
    private byte[] getSeqNumbers(byte seqNum, byte numChunks) {

        return padSeq ? new byte[]{0x00, seqNum, 0x00, numChunks} : new byte[]{seqNum, numChunks};
    }
}
