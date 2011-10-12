package org.logbackgelf;

import java.io.IOException;
import java.net.*;
import java.util.List;

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
     * @param data gzipped GELF Message (JSON)
     */
    public void send(byte[] data) {
        InetAddress address = getInetAddress(host);
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
        sendPacket(datagramPacket);
    }

    /**
     * Sends a JSON GELF message to the graylog2 server
     *
     * @param packets The packets to send over the wire
     */
    public void send(List<byte[]> packets) {
        System.out.println("sending " + packets.size() + " packets");
        for(byte[] packet : packets) {
            send(packet);
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
