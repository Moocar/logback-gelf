package org.logbackgelf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.zip.GZIPOutputStream;

public class Transport {

    private final String host;
    private final int port;

    public Transport(String host, int port) {
        this.port = port;
        this.host = host;
    }

    /**
     * Sends a JSON GELF message to the graylog2 server
     *
     * @param message The GELF Message (JSON)
     */
    public void send(String message) {
        byte[] data = gzipString(message);
        InetAddress address = getInetAddress(host);
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
        sendPacket(datagramPacket);
    }

    /**
     * zips up a string into a GZIP format.
     *
     * @param str The string to zip
     * @return The zipped string
     */
    private byte[] gzipString(String str) {
        GZIPOutputStream zipStream = null;
        try {
            ByteArrayOutputStream targetStream = new ByteArrayOutputStream();
            zipStream = new GZIPOutputStream(targetStream);
            zipStream.write(str.getBytes());
            zipStream.close();
            byte[] zipped = targetStream.toByteArray();
            targetStream.close();
            return zipped;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            try {
                if (zipStream != null) {
                    zipStream.close();
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private InetAddress getInetAddress(String hostname) {
        try {
            return InetAddress.getByName(hostname);
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void sendPacket(DatagramPacket datagramPacket) {
        DatagramSocket datagramSocket = getDatagramSocket();
        try {
            datagramSocket.send(datagramPacket);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            datagramSocket.close();
        }
    }

    private DatagramSocket getDatagramSocket() {
        try {
            return new DatagramSocket();
        } catch (SocketException ex) {
            throw new RuntimeException(ex);
        }
    }
}
