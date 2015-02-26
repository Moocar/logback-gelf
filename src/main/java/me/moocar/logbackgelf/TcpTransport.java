package me.moocar.logbackgelf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class TcpTransport implements Transport {
    private final InetAddress graylog2ServerAddress;
    private final int graylog2ServerPort;
    private final Lock lock = new ReentrantLock();
    private Socket socket;

    public TcpTransport(int graylog2ServerPort, InetAddress graylog2ServerAddress) {
        this.graylog2ServerPort = graylog2ServerPort;
        this.graylog2ServerAddress = graylog2ServerAddress;
    }


    @Override
    public void send(byte[] data) {
        ensureConnected(s -> {
            try {
                s.getOutputStream().write(data);
            } catch (IOException e) {
                // Silently fail, same as UDP
            }
            return null;
        });
    }

    private void ensureConnected(Function<Socket, Void> function) {
        try {
            lock.lock();
            try {
                if (invalidSocket(socket)) {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        // Ignore, closing socket.
                    }
                    socket = null;
                }
                if (socket == null) {
                    socket = new Socket(graylog2ServerAddress, graylog2ServerPort);
                }
            } finally {
                lock.unlock();
            }
            function.apply(socket);
        } catch (IOException e) {
            // Silently fail, same as UDP
        }
    }

    private boolean invalidSocket(Socket socket) {
        return socket != null && (socket.isClosed() || !socket.isConnected());
    }

    @Override
    public void send(List<byte[]> packets) {
        ensureConnected(s -> {
            try {
                for (byte[] packet : packets) {
                    s.getOutputStream().write(packet);
                }
            } catch (IOException e) {
                // Silently fail, same as UDP
            }
            return null;
        });
    }
}
