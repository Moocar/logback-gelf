package me.moocar.logbackgelf;

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
    private final int maxChunks;
    private final MessageIdProvider messageIdProvider;
    private final ChunkFactory chunkFactory;

    public PayloadChunker(int payloadThreshold, int maxChunks, MessageIdProvider messageIdProvider,
                          ChunkFactory chunkFactory) {

        this.payloadThreshold = payloadThreshold;
        this.maxChunks = maxChunks;
        this.messageIdProvider = messageIdProvider;
        this.chunkFactory = chunkFactory;
    }

    /**
     * Converts a payload into a number of full GELF chunks.
     *
     * @param payload The original gzipped payload
     * @return A list of chunks that should be sent to the graylog2 server
     */
    public List<byte[]> chunkIt(byte[] payload) {

        return createChunks(messageIdProvider.get(), splitPayload(payload));
    }

    /**
     * Converts each subPayload into a full GELF chunk
     *
     * @param messageId The unique messageId that all the produced chunks will use
     * @param subPayloads The raw sub payload data.
     * @return A list of each subPayload wrapped in a GELF chunk
     */
    private List<byte[]> createChunks(byte[] messageId, List<byte[]> subPayloads) {
        List<byte[]> chunks = new ArrayList<byte[]>();

        byte seqNum = 0;

        for(byte[] subPayload : subPayloads) {

            if (seqNum == maxChunks) {
                break;
            }

            chunks.add(chunkFactory.create(messageId, seqNum++, (byte) (subPayloads.size()), subPayload));
        }

        return chunks;
    }

    /**
     * Splits the payload data into chunks
     *
     * @param payload The full payload
     * @return A list of subPayloads which when added together, make up the full payload
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
