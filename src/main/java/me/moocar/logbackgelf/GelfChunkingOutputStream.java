package me.moocar.logbackgelf;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;

/**
 * An OutputStream that sends bytes as UDP packets to a [GELF](https://www.graylog.org/resources/gelf/) compatible
 * remote server. Use flush() to signify the end of a message. If the total number of bytes in the message is less than
 * maxPacketSize, they will be sent in one datagram. If more, the bytes will be broken up into GELF chunks and sent all
 * at once when flush() is called.
 *
 * Note that this class is NOT thread safe. A sequential process should call flush() before another starts writing.
 */
public class GelfChunkingOutputStream extends OutputStream {

    // GELF specifies a maximum number of chunks. Chunks will be dropped on the server once more than MAX_CHUNKS are
    // sent to the server for a particular message
    private final int MAX_CHUNKS = 128;
    private final byte[] CHUNKED_GELF_ID_BYTES = new byte[]{0x1e, 0x0f};
    private final int MESSAGE_ID_LENGTH = 8;
    private final int SEQ_COUNT_POSITION = CHUNKED_GELF_ID_BYTES.length + MESSAGE_ID_LENGTH + 1;

    // Bytes will be be added to this byte array until maxPacketSize is reached, at which point chunk mode will be
    // turned on, and the bytes will be replayed
    private final byte[] packetBytes;
    // Once chunk mode has been turned on (once maxPacketSize bytes have been written to this output stream), bytes will
    // be added to this 2D byte array. The first dimension is the current chunk sequence we're up to. The second
    // dimension is the bytes in that particular chunk
    private final byte[][] chunks;

    private final int maxPacketSize;
    private final MessageIdProvider messageIdProvider;

    private final InetAddress address;
    private final int port;
    private DatagramSocket socket;

    // When in chunking mode, this is the index of the chunk that we are currently writing bytes to
    private int chunkIndex = 0;
    // The position in the chunk or packetBytes that the next byte will be put into
    private int position = 0;
    // After a flush, chunking is turned off. It is only turned on once more than maxPacketSize bytes are written to
    // this output stream
    private boolean chunked = false;
    // True when more so many bytes have been written that we've exceeded MAX_CHUNKS
    private boolean maxChunksReached = false;
    // Once chunking is turned on, this will have the messageID that should be used for all of this message's chunks
    private byte[] messageID;

    /**
     * Create a new GelfChunkingOutputStream
     *
     * @param address The address of the remove server
     * @param port The port of the remote server
     * @param maxPacketSize The maximum number of bytes allowed before chunking begins
     * @param messageIdProvider A object that generates totally unique (for this machine) 8-byte message IDs.
     */
    public GelfChunkingOutputStream(InetAddress address, int port, int maxPacketSize, MessageIdProvider messageIdProvider) {
        this.address = address;
        this.port = port;
        this.maxPacketSize = maxPacketSize;
        this.messageIdProvider = messageIdProvider;
        this.chunks = new byte[MAX_CHUNKS][maxPacketSize];
        this.packetBytes = new byte[maxPacketSize];
    }


    public void start() throws SocketException, UnknownHostException {
        this.socket = new DatagramSocket();
        this.socket.connect(address, port);
    }

    @Override
    public void write(int b) throws IOException {
        if (maxChunksReached) {
            return;
        }
        if (!chunked) {
            writeUnchunked((byte) b);

        } else {
            writeChunked((byte) b);
        }
    }

    private void writeUnchunked(byte b) throws IOException {
        if (position < maxPacketSize) {
            packetBytes[position++] = b;
        } else {
            startChunking();
            write(b);
        }
    }

    private void startChunking() throws IOException {
        chunked = true;
        messageID = messageIdProvider.get();
        chunkIndex = -1;
        // pour the bytes back through in chunking mode. packetBytes is now ignored until flush finishes.
        for (byte packetByte : packetBytes) {
            write(packetByte);
        }

    }

    private void writeChunked(byte b) throws IOException {
        if (position == maxPacketSize) {
            if (chunkIndex == MAX_CHUNKS - 1) {
                maxChunksReached = true;
            } else {
                chunkIndex++;
                position = 0;
                writeHeader();
                write(b);
            }
        } else {
            chunks[chunkIndex][position++] = b;
        }
    }

    private void writeHeader() {
        chunks[chunkIndex][position++] = CHUNKED_GELF_ID_BYTES[0];
        chunks[chunkIndex][position++] = CHUNKED_GELF_ID_BYTES[1];
        for (byte messageIDByte : messageID) {
            chunks[chunkIndex][position++] = messageIDByte;
        }
        chunks[chunkIndex][position++] = (byte) chunkIndex;
        position++; // for the sequence count which will be added in flush
    }

    @Override
    public void flush() throws IOException {
        try {
            // Calling flush but write has not been called. do nothing
            if (chunkIndex == 0 && position == 0) {
                return;
            }

            // Too many bytes written. Drop everything
            if (maxChunksReached) {
                return;
            }

            if (chunked) {
                flushChunked();

            } else {
                sendBytes(packetBytes, position);
            }

        } finally {
            reset();
        }

    }

    private void flushChunked() throws IOException {
        fillInSequenceCounts();
        for (int i = 0; i < chunkIndex; i++) {
            sendBytes(chunks[i], maxPacketSize);
        }
        sendBytes(chunks[chunkIndex], position);

    }

    private void fillInSequenceCounts() {
        for (int i = 0; i <= chunkIndex; i++) {
            chunks[i][SEQ_COUNT_POSITION] = (byte) (chunkIndex + 1);
        }
    }

    private void sendBytes(byte[] bytes, int length) throws IOException {
        DatagramPacket packet = new DatagramPacket(bytes, 0, length);
        socket.send(packet);
    }

    private void reset() {
        position = 0;
        chunkIndex = 0;
        chunked = false;
        maxChunksReached = false;
    }
}
