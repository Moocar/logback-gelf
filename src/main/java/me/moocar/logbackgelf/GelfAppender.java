package me.moocar.logbackgelf;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Responsible for Formatting a log event and sending it to a Graylog2 Server. Note that you can't swap in a different
 * Layout since the GELF format is static.
 */
public class GelfAppender extends BaseAppender {

    // The following are configurable via logback configuration
    private String graylog2ServerHost = "localhost";
    private int graylog2ServerPort = 12201;
    private int chunkThreshold = 1000;

    // The following are hidden (not configurable)
    private static final int maxChunks = 127;
    private final byte[] chunkedGelfId = new byte[]{0x1e, 0x0f};

    private UDPAppenderExecutor appenderExecutor;

    /**
     * The main append method. Takes the event that is being logged, formats if for GELF and then sends it over the wire
     * to the log server
     *
     * @param logEvent The event that we are logging
     */
    @Override
    protected void append(ILoggingEvent logEvent) {

        try {

            appenderExecutor.append(logEvent);

        } catch (RuntimeException e) {
            System.out.println(getStringStackTrace(e));
            this.addError("Error occurred: ", e);
            throw e;
        }
    }

    /**
     * This is an ad-hoc dependency injection mechanism. We don't want create all these classes every time a message is
     * logged. They will hang around for the lifetime of the appender.
     */
    protected void initExecutor() {

        try {
            super.initExecutor();

            InetAddress address = getInetAddress();

            UDPTransport transport = new UDPTransport(graylog2ServerPort, address);

            String hostname = getLocalHostName();

            PayloadChunker payloadChunker = new PayloadChunker(chunkThreshold, maxChunks,
                    new MessageIdProvider(messageIdLength, MessageDigest.getInstance("MD5"), hostname),
                    new ChunkFactory(chunkedGelfId, padSeq));

            GelfConverter converter = new GelfConverter(facility, useLoggerName, useThreadName, additionalFields,
                    staticAdditionalFields, shortMessageLength, hostname, messagePattern, shortMessagePattern,
                    includeFullMDC);

            appenderExecutor = new UDPAppenderExecutor(transport, payloadChunker, converter, new Zipper(), chunkThreshold);

        } catch (Exception e) {

            throw new RuntimeException("Error initialising appender appenderExecutor", e);
        }
    }

    /**
     * Gets the Inet address for the graylog2ServerHost and gives a specialised error message if an exception is thrown
     *
     * @return The Inet address for graylog2ServerHost
     */
    private InetAddress getInetAddress() {
        try {
            return InetAddress.getByName(graylog2ServerHost);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Unknown host: " + e.getMessage() +
                    ". Make sure you have specified the 'graylog2ServerHost' property correctly in your logback.xml'");
        }
    }

    //////////// Logback Property Getter/Setters ////////////////

    /**
     * The hostname of the graylog2 server to send messages to
     */
    public String getGraylog2ServerHost() {
        return graylog2ServerHost;
    }

    public void setGraylog2ServerHost(String graylog2ServerHost) {
        this.graylog2ServerHost = graylog2ServerHost;
    }

    /**
     * The port of the graylog2 server to send messages to
     */
    public int getGraylog2ServerPort() {
        return graylog2ServerPort;
    }

    public void setGraylog2ServerPort(int graylog2ServerPort) {
        this.graylog2ServerPort = graylog2ServerPort;
    }

    public int getChunkThreshold() {
        return chunkThreshold;
    }

    public void setChunkThreshold(int chunkThreshold) {
        this.chunkThreshold = chunkThreshold;
    }
}
