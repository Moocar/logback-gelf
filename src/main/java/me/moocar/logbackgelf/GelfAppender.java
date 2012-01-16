package me.moocar.logbackgelf;

import ch.qos.logback.core.AppenderBase;
import com.google.common.base.Throwables;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for Formatting a log event and sending it to a Graylog2 Server. Note that you can't swap in a different
 * Layout since the GELF format is static.
 */
public class GelfAppender<E> extends AppenderBase<E> {

    // The following are configurable via logback configuration
    private String facility = "GELF";
    private String graylog2ServerHost = "localhost";
    private int graylog2ServerPort = 12201;
    private boolean useLoggerName = false;
    private boolean useThreadName = false;
    private String graylog2ServerVersion = "0.9.5";
    private int chunkThreshold = 1000;
    private Map<String, String> additionalFields = new HashMap<String, String>();

    // The following are hidden (not configurable)
    private int shortMessageLength = 255;
    private static final int maxChunks = 127;
    private int messageIdLength = 32;
    private boolean padSeq = true;
    private final byte[] chunkedGelfId = new byte[]{0x1e, 0x0f};

    private AppenderExecutor<E> appenderExecutor;

    /**
     * The main append method. Takes the event that is being logged, formats if for GELF and then sends it over the wire
     * to the log server
     *
     * @param logEvent The event that we are logging
     */
    @Override
    protected void append(E logEvent) {

        try {

            appenderExecutor.append(logEvent);

        } catch (RuntimeException e) {
            System.out.println(e.getMessage() + ": " + Throwables.getStackTraceAsString(e));
            this.addError("Error occurred: ", e);
            throw e;
        }
    }

    @Override
    public void start() {
        super.start();
        initExecutor();
    }

    /**
     * This is an ad-hoc dependency injection mechanism. We don't want create all these classes every time a message is
     * logged. They will hang around for the lifetime of the appender.
     */
    private void initExecutor() {

        try {

            InetAddress address = getInetAddress();

            Transport transport = new Transport(graylog2ServerPort, address);

            if (graylog2ServerVersion.equals("0.9.6")) {
                messageIdLength = 8;
                padSeq = false;
            }

            String hostname = InetAddress.getLocalHost().getHostName();

            PayloadChunker payloadChunker = new PayloadChunker(chunkThreshold, maxChunks,
                    new MessageIdProvider(messageIdLength, MessageDigest.getInstance("MD5"), hostname),
                    new ChunkFactory(chunkedGelfId, padSeq));

            GelfConverter converter = new GelfConverter(facility, useLoggerName, useThreadName, additionalFields, shortMessageLength, hostname);

            appenderExecutor = new AppenderExecutor<E>(transport, payloadChunker, converter, new Zipper(), chunkThreshold);

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
     * The name of your service. Appears in facility column in graylog2-web-interface
     */
    public String getFacility() {
        return facility;
    }

    public void setFacility(String facility) {
        this.facility = facility;
    }

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

    /**
     * If true, an additional field call "_loggerName" will be added to each gelf message. Its contents will be the
     * fully qualified name of the logger. e.g: com.company.Thingo.
     */
    public boolean isUseLoggerName() {
        return useLoggerName;
    }

    public void setUseLoggerName(boolean useLoggerName) {
        this.useLoggerName = useLoggerName;
    }
    
    /**
     * If true, an additional field call "_threadName" will be added to each gelf message. Its contents will be the
     * Name of the thread. Defaults to "false".
     */
    public boolean isUseThreadName() {
    	return useThreadName;
    }
    
    public void setUseThreadName(boolean useThreadName) {
    	this.useThreadName = useThreadName;
    }

    /**
     * additional fields to add to the gelf message. Here's how these work: <br/> Let's take an example. I want to log
     * the client's ip address of every request that comes into my web server. To do this, I add the ipaddress to the
     * slf4j MDC on each request as follows: <code> ... MDC.put("ipAddress", "44.556.345.657"); ... </code> Now, to
     * include the ip address in the gelf message, i just add the following to my logback.groovy: <code>
     * appender("GELF", GelfAppender) { ... additionalFields = [identity:"_identity"] ... } </code> in the
     * additionalFields map, the key is the name of the MDC to look up. the value is the name that should be given to
     * the key in the additional field in the gelf message.
     */
    public Map<String, String> getAdditionalFields() {
        return additionalFields;
    }

    public void setAdditionalFields(Map<String, String> additionalFields) {
        this.additionalFields = additionalFields;
    }

    /**
     * Add an additional field. This is mainly here for compatibility with logback.xml
     *
     * @param keyValue This must be in format key:value where key is the MDC key, and value is the GELF field
     * name. e.g "ipAddress:_ip_address"
     */
    public void addAdditionalField(String keyValue) {
        String[] splitted = keyValue.split(":");

        if (splitted.length != 2) {

            throw new IllegalArgumentException("additionalField must be of the format key:value, where key is the MDC "
                    + "key, and value is the GELF field name. But found '" + keyValue + "' instead.");
        }

        additionalFields.put(splitted[0], splitted[1]);
    }

    /**
     * The length of the message to truncate to
     */
    public int getShortMessageLength() {
        return shortMessageLength;
    }

    public void setShortMessageLength(int shortMessageLength) {
        this.shortMessageLength = shortMessageLength;
    }

    public String getGraylog2ServerVersion() {
        return graylog2ServerVersion;
    }

    public void setGraylog2ServerVersion(String graylog2ServerVersion) {
        this.graylog2ServerVersion = graylog2ServerVersion;
    }

    public int getChunkThreshold() {
        return chunkThreshold;
    }

    public void setChunkThreshold(int chunkThreshold) {
        this.chunkThreshold = chunkThreshold;
    }
}
