package me.moocar.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Locale;
import java.util.List;

/**
 * Responsible for sending packet(s) to the graylog2 server
 */
public class UDPTransport implements Transport {

    private final InetAddress graylog2ServerAddress;
    private final int graylog2ServerPort;
    private final SocketAddress loopbackAddress = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).indexOf("windows") > -1 ?
        new InetSocketAddress("localhost", 0) : new InetSocketAddress(0);

    public UDPTransport(int graylog2ServerPort, InetAddress graylog2ServerAddress) {
        this.graylog2ServerPort = graylog2ServerPort;
        this.graylog2ServerAddress = graylog2ServerAddress;
    }

    /**
     * Sends a single packet GELF message to the graylog2 server
     *
     * @param data gzipped GELF Message (JSON)
     */
    public void send(byte[] data, ILoggingEvent event) {

        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, graylog2ServerAddress, graylog2ServerPort);

        sendPacket(datagramPacket);
    }

    /**
     * Sends a bunch of GELF Chunks to the graylog2 server
     *
     * @param packets The packets to send over the wire
     */
    public void send(List<byte[]> packets, ILoggingEvent event) {

        for(byte[] packet : packets) {

            send(packet, event);
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

            return new DatagramSocket(loopbackAddress);

        } catch (SocketException ex) {

            throw new RuntimeException(ex);
        }
    }
}
