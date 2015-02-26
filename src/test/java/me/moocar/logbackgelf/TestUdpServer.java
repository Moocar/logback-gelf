package me.moocar.logbackgelf;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Created with IntelliJ IDEA.
 * User: anthony
 * Date: 3/2/13
 * Time: 12:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestUdpServer implements TestServer {

    private final ServerThread serverThread;

    public TestUdpServer(ServerThread serverThread) {
        this.serverThread = serverThread;
    }

    @Override
    public void start() {
        this.serverThread.start();
    }

    public static TestUdpServer build() throws SocketException {
        return new TestUdpServer(new ServerThread(new DatagramSocket(12201)));
    }

    public ImmutableMap<String, Object> lastRequest() {
        return serverThread.lastRequest;
    }

    public void shutdown() {
        serverThread.stopServer = true;
    }

    private static class ServerThread extends Thread {

        private final DatagramSocket socket;
        private ImmutableMap<String, Object> lastRequest;
        private boolean stopServer = false;
        private static final int maxPacketSize = 8192;
        private final Gson gson;

        public ServerThread(DatagramSocket socket) {
            this.socket = socket;
            this.gson = new Gson();
        }

        @Override
        public void run() {
            while (!stopServer) {
                byte[] receivedData = new byte[maxPacketSize];
                DatagramPacket packet = new DatagramPacket(receivedData, maxPacketSize);
                try {
                    socket.receive(packet);
                } catch (IOException e) {
                    lastRequest = ImmutableMap.of("Error", (Object)e.getMessage());
                }
                if (receivedData[0] == (byte) 0x1f && receivedData[1] == (byte) 0x8b) {
                    String decompressed = decompressGzip(packet.getData());

                    Map map = gson.fromJson(decompressed, Map.class);
                    lastRequest = ImmutableMap.copyOf(map);// ImmutableMap.of("Zipped?", true, "length", packet.getLength(), "body", decompressed, "map", map);
                }

            }

        }

        public static String decompressGzip(byte[] inputBuf) {
            byte[] buffer = new byte[inputBuf.length];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(inputBuf));
                try {
                    for (int bytesRead = 0; bytesRead != -1; bytesRead = in.read(buffer)) {
                        out.write(buffer, 0, bytesRead);
                    }
                    return new String(out.toByteArray(), "UTF-8");
                } catch (Exception e) {
                    return "Error reading bytes" + e.getMessage();
                }
            } catch (IOException e) {
                return "Error creating input stream" + e.getMessage();
            }
        }

    }
}