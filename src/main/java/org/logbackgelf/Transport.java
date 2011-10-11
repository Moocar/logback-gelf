package org.logbackgelf;

import java.io.IOException;
import java.net.*;

/**
 * Created by IntelliJ IDEA. User: anthony Date: 10/11/11 Time: 9:43 PM To change this template use File | Settings |
 * File Templates.
 */
public class Transport {

    /**
     * Sends a gzipped byte array containing the GELF message to the graylog2 server
     *
     * @param data The gizpped byte array. Must encode a GELF message.
     */
    public void sendPacket(byte[] data, String host, int port) {
        InetAddress address = getInetAddress(host);
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
        sendPacket(datagramPacket);
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
