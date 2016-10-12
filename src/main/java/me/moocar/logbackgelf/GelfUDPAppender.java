package me.moocar.logbackgelf;

import ch.qos.logback.core.OutputStreamAppender;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;

/**
 * A UDP appender that sends logs to a remote UDP server. Slices messages into multiple chunks if they're too big. See
 * GelfChunkingOutputStream for how chunking works.
 *
 * @param <E>
 */
public class GelfUDPAppender<E> extends OutputStreamAppender<E> {

    private final String REMOTE_HOST = "localhost";
    private final int DEFAULT_PORT = 12201;
    private final int DEFAULT_SRC_PORT = 0;
    private final int DEFAULT_MAX_PACKET_SIZE = 512;

    private String remoteHost = REMOTE_HOST;
    private int port = DEFAULT_PORT;
    private int srcPort = DEFAULT_SRC_PORT;
    private int maxPacketSize = DEFAULT_MAX_PACKET_SIZE;

    @Override
    public void start() {
        if (isStarted()) return;
        int errorCount = 0;
        if (port <= 0) {
            errorCount++;
            addError("No port was configured for appender"
                    + name
                    + " For more information, please visit http://logback.qos.ch/codes.html#socket_no_port");

        }

        if (remoteHost == null) {
            errorCount++;
            addError("No remote host was configured for appender"
                    + name
                    + " For more information, please visit http://logback.qos.ch/codes.html#socket_no_host");
        }
        InetAddress address = null;
        if (errorCount == 0) {
            try {
                address = InternetUtils.getInetAddress(remoteHost);
            } catch (Exception e) {
                addError(e.getMessage());
                errorCount++;
            }
        }

        String hostname = null;
        if (errorCount == 0) {
            try {
                hostname = InternetUtils.getLocalHostName();
            } catch (SocketException e) {
                addError("Error creating localhostname", e);
                errorCount++;
            } catch (UnknownHostException e) {
                addError("Could not create hostname");
                errorCount++;
            }
        }

        MessageIdProvider messageIdProvider = null;
        if (errorCount == 0) {
            try {
                messageIdProvider = new MessageIdProvider(hostname);
            } catch (NoSuchAlgorithmException e) {
                errorCount++;
                addError("Error creating digest", e);
            }
        }

        if (errorCount == 0) {
            GelfChunkingOutputStream os = new GelfChunkingOutputStream(address, port, srcPort, maxPacketSize, messageIdProvider);
            try {
                os.start();
                this.setOutputStream(os);
                super.start();
            } catch (SocketException e) {
                addError("Could not connect to remote host", e);
            } catch (UnknownHostException e) {
                addError("unknown host: " + remoteHost);
            }
        }


    }

    @Override
    protected void writeOut(E event) {
        try {
            super.writeOut(event);
        } catch (IOException e) {
            addError("IO Exception in UDP output stream", e);
        }
    }

    /**
     * The remote host name to send logs to. Defaults to "localhost"
     */
    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    /**
     * The remote port to send logs to. Defaults to 12201
     */
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * The source port on the local machine. Defaults to 0 (ephemeral port)
     * @see java.net.InetSocketAddress#InetSocketAddress(int port)
     */
    public int getSrcPort() {
        return srcPort;
    }

    public void setSrcPort(int srcPort) {
        this.srcPort = srcPort;
    }

    /**
     * Maximum packet size. Defaults to 512 (for a maximum 64kb log after chunking).
     */
    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public void setMaxPacketSize(int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }
}
