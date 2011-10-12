package org.logbackgelf;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PacketCreator {

    private final int chunkThreshold;
    public final static byte[] CHUNKED_GELF_ID = new byte[]{0x1e, 0x0f};
    public final static int MESSAGE_ID_LENGTH = 32;

    public PacketCreator(int chunkThreshold) {

        this.chunkThreshold = chunkThreshold;
    }

    public List<byte[]> go(byte[] data) {
        List<byte[]> packets = new ArrayList<byte[]>();

        if (data.length > chunkThreshold) {
            packets.addAll(chunkIt(data));
        } else {
            packets.add(data);
        }
        return packets;
    }

    private List<byte[]> chunkIt(byte[] data) {
        byte[] messageId = createMessageId();
        List<byte[]> packets = new ArrayList<byte[]>();
        List<byte[]> chunkDatas = splitData(data);
        byte seqNum = 0;
        for(byte[] chunkData : chunkDatas) {
            packets.add(createChunk(messageId, seqNum, (byte)chunkDatas.size(), chunkData));
            seqNum++;
        }
        return packets;
    }

    private byte[] createMessageId() {
        try {
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

    private byte[] createChunk(byte[] messageId, byte seqNum, byte numChunks, byte[] chunk) {
        return  concatArrays(concatArrays(concatArrays(CHUNKED_GELF_ID, messageId), new byte[]{0x00, seqNum, 0x00, numChunks}), chunk);
    }

    private byte[] concatArrays(byte[] array1, byte[] array2) {
        byte[] finalArray = Arrays.copyOf(array1, array2.length + array1.length);
        System.arraycopy(array2, 0, finalArray, array1.length, array2.length);
        return finalArray;
    }

    private List<byte[]> splitData(byte[] data) {
        List<byte[]> chunkedData = new ArrayList<byte[]>();

        int dataLength = data.length;
        int numFullChunks = dataLength / chunkThreshold;
        int finalChunkLength = dataLength % chunkThreshold;

        for (int i=0; i < numFullChunks; i++) {
            addChunk(data, chunkedData, i);
        }

        if (finalChunkLength > 0) {
            addChunk(data, chunkedData, numFullChunks);
        }

        return chunkedData;
    }

    private void addChunk(byte[] data, List<byte[]> chunkedData, int i) {
        chunkedData.add(extractChunk(data, i));
    }

    private byte[] extractChunk(byte[] data, int i) {
        return Arrays.copyOfRange(data, i * chunkThreshold, (i + 1) * chunkThreshold);
    }
}
