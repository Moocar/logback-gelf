package me.moocar.logbackgelf;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

public class GelfUDPOutputStream extends OutputStream {

    private final LinkedBlockingDeque<DatagramPacket> deque;

    private final InetAddress address;
    private final int port;
    private DatagramSocket socket;
    private final MessageIdProvider messageIdProvider;
    private final int maxChunks = 127;
    private final byte[] chunkedGelfId = new byte[]{0x1e, 0x0f};;
    private final int chunkThreshold = 8100;

    public GelfUDPOutputStream(InetAddress address, int port, int queueSize, MessageIdProvider messageIdProvider) {
        super();
        this.address = address;
        this.port = port;
        this.messageIdProvider = messageIdProvider;
        this.deque = new LinkedBlockingDeque<DatagramPacket>(queueSize);
    }

    public void start() throws SocketException, UnknownHostException {
        this.socket = new DatagramSocket();
        this.socket.connect(address, port);
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte)b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        // If we can fit all the information into one packet, then just send it
        if (b.length < chunkThreshold) {

            DatagramPacket packet = new DatagramPacket(b, off, len);
            deque.offer(packet);

        // If the message is too long, then slice it up and send multiple packets
        } else {
            List<byte[]> payloads = chunkIt(b);

            for (byte[] bytes : payloads) {
                DatagramPacket packet = new DatagramPacket(bytes, 0, bytes.length);
                deque.offer(packet);
            }
        }
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

            chunks.add(createChunk(messageId, seqNum++, (byte) (subPayloads.size()), subPayload));
        }

        return chunks;
    }


    /**
     * Concatenates everything into a GELF Chunk header and then appends the sub Payload
     * @param messageId The message ID of the message (remains same for all chunks)
     * @param seqNum The sequence number of the chunk
     * @param numChunks The number of chunks that will be sent
     * @param subPayload The actual data that will be sent in this chunk
     * @return The complete chunk that wraps the sub pay load
     */
    public byte[] createChunk(byte[] messageId, byte seqNum, byte numChunks, byte[] subPayload) {

        return  concatArrays(concatArrays(concatArrays(chunkedGelfId, messageId), new byte[]{seqNum, numChunks}), subPayload);
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
     * Splits the payload data into chunks
     *
     * @param payload The full payload
     * @return A list of subPayloads which when added together, make up the full payload
     */
    private List<byte[]> splitPayload(byte[] payload) {
        List<byte[]> subPayloads = new ArrayList<byte[]>();

        int payloadLength = payload.length;
        int numFullSubs = payloadLength / chunkThreshold;
        int lastSubLength = payloadLength % chunkThreshold;

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
        return Arrays.copyOfRange(payload, subPaylod * chunkThreshold, (subPaylod + 1) * chunkThreshold);
    }

    @Override
    public synchronized void flush() throws IOException {
        DatagramPacket packet;
        while ((packet = deque.poll()) != null ) {
            socket.send(packet);
        }
    }
}
