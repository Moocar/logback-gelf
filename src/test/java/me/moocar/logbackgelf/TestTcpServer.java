package me.moocar.logbackgelf;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class TestTcpServer implements TestServer {

    private final ServerThread serverThread;

    public TestTcpServer(ServerThread serverThread) {
        this.serverThread = serverThread;
    }

    public void start() {
        this.serverThread.start();
    }

    public static TestTcpServer build() throws IOException {
        return new TestTcpServer(new ServerThread(new ServerSocket(12201)));
    }

    public ImmutableMap<String, Object> lastRequest() {
        return serverThread.lastRequest;
    }

    public void shutdown() {
        serverThread.stopServer = true;
    }

    private static class ServerThread extends Thread {

        private final ServerSocket socket;
        private ImmutableMap<String, Object> lastRequest;
        private boolean stopServer = false;
        private static final int maxPacketSize = 8192;
        private final Gson gson;

        public ServerThread(ServerSocket socket) {
            this.socket = socket;
            this.gson = new Gson();
        }

        @Override
        public void run() {
            while (!stopServer) {
                try {
                    Socket accept = socket.accept();
                    byte[] receivedData = new byte[maxPacketSize];
                    try {
                        accept.getInputStream().read(receivedData);
                    } catch (IOException e) {
                        lastRequest = ImmutableMap.of("Error", (Object)e.getMessage());
                    }
                    if (receivedData[0] == (byte) 0x1f && receivedData[1] == (byte) 0x8b) {
                        String decompressed = decompressGzip(receivedData);

                        Map map = gson.fromJson(decompressed, Map.class);
                        lastRequest = ImmutableMap.copyOf(map);// ImmutableMap.of("Zipped?", true, "length", packet.getLength(), "body", decompressed, "map", map);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
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