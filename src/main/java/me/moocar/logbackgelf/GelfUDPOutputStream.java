package me.moocar.logbackgelf;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;

public class GelfUDPOutputStream extends OutputStream{


    private final int MAX_CHUNKS = 128;
    private final byte[] CHUNKED_GELF_ID_BYTES = new byte[]{0x1e, 0x0f};
    private final int MESSAGE_ID_LENGTH = 8;
    private final int SEQ_COUNT_POSITION = CHUNKED_GELF_ID_BYTES.length + MESSAGE_ID_LENGTH + 1;
    private final byte[] packetBytes;
    private final byte[][] chunks;
    private final int maxPacketSize;
    private final MessageIdProvider messageIdProvider;

    private final InetAddress address;
    private final int port;
    private DatagramSocket socket;

    private int chunkIndex = 0;
    private int position = 0;
    private boolean chunked = false;
    private boolean maxChunksReached = false;
    private byte[] messageID;

    public GelfUDPOutputStream(InetAddress address, int port, int maxPacketSize, MessageIdProvider messageIdProvider) {
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
        position++; // for the sequence count which will come later
    }

    @Override
    public void flush() throws IOException {
        try {
            if (chunkIndex == 0 && position == 0) {
                return;
            }
            if (maxChunksReached) {
                return;
            }

            if (chunked) {
                fillInSequenceCounts();
                for (int i = 0; i < chunkIndex; i++) {
                    sendBytes(chunks[i], maxPacketSize);
                }
                sendBytes(chunks[chunkIndex], position);

            } else {
                sendBytes(packetBytes, position);
            }
        } finally {
            position = 0;
            chunkIndex = 0;
            chunked = false;
            maxChunksReached = false;
        }

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
}
