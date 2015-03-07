package me.moocar.logbackgelf;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
    public void send(final byte[] data) {
        ensureConnected(new SocketFunction() {
            @Override
            public void call(Socket s) {
                try {
                    writeNullTerminated(data, s.getOutputStream());
                } catch (IOException e) {
                    handleException(e);
                }
            }
        });
    }

    private void ensureConnected(SocketFunction function) {
        try {
            lock.lock();
            try {
                if (invalidSocket(socket)) {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        handleException(e);
                    }
                    socket = null;
                }
                if (socket == null) {
                    socket = new Socket(graylog2ServerAddress, graylog2ServerPort);
                }
            } finally {
                lock.unlock();
            }
            function.call(socket);
        } catch (IOException e) {
            handleException(e);
        }
    }

    private boolean invalidSocket(Socket socket) {
        return socket != null && (socket.isClosed() || !socket.isConnected());
    }

    @Override
    public void send(final List<byte[]> packets) {
        ensureConnected(new SocketFunction() {
            @Override
            public void call(Socket s) {
                try {
                    for (byte[] packet : packets) {
                        writeNullTerminated(packet, s.getOutputStream());
                    }
                } catch (IOException e) {
                    handleException(e);
                }
            }
        });
    }

    private void handleException(Exception e) {
        // Silently fail, same as UDP
//        e.printStackTrace();
    }

    private void writeNullTerminated(byte[] packet, OutputStream outputStream) throws IOException {
        byte[] nullTerminated = new byte [packet.length + 1];
        System.arraycopy(packet, 0, nullTerminated, 0, packet.length);
        nullTerminated[packet.length] = 0;
        outputStream.write(nullTerminated);
    }

    interface SocketFunction {
        public void call(Socket socket);
    }
}

