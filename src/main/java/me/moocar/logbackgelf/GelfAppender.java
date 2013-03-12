package me.moocar.logbackgelf;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Responsible for Formatting a log event and sending it to a Graylog2 Server. Note that you can't swap in a different
 * Layout since the GELF format is static.
 */
public class GelfAppender extends AppenderBase<ILoggingEvent> {

    // The following are configurable via logback configuration
    private String facility = "GELF";
    private String graylog2ServerHost = "localhost";
    private int graylog2ServerPort = 12201;
    private boolean useLoggerName = false;
    private boolean useThreadName = false;
    private String graylog2ServerVersion = "0.9.6";
    private int chunkThreshold = 1000;
    private String messagePattern = "%m%rEx";
    private Map<String, String> additionalFields = new HashMap<String, String>();
    private Map<String, String> staticAdditionalFields = new HashMap<String, String>();
    private boolean includeFullMDC;

    // The following are hidden (not configurable)
    private int shortMessageLength = 255;
    private static final int maxChunks = 127;
    private int messageIdLength = 8;
    private boolean padSeq = false;
    private final byte[] chunkedGelfId = new byte[]{0x1e, 0x0f};

    private AppenderExecutor appenderExecutor;

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

    private String getStringStackTrace(Exception e) {
        Writer result = new StringWriter();
        PrintWriter printWriter = new PrintWriter(result);
        e.printStackTrace(printWriter);
        return result.toString();
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

            if (graylog2ServerVersion.equals("0.9.5")) {
                messageIdLength = 32;
                padSeq = true;
            }

            String hostname = getLocalHostName();

            PayloadChunker payloadChunker = new PayloadChunker(chunkThreshold, maxChunks,
                    new MessageIdProvider(messageIdLength, MessageDigest.getInstance("MD5"), hostname),
                    new ChunkFactory(chunkedGelfId, padSeq));

            GelfConverter converter = new GelfConverter(facility, useLoggerName, useThreadName, additionalFields,
                    staticAdditionalFields, shortMessageLength, hostname, messagePattern, includeFullMDC);

            appenderExecutor = new AppenderExecutor(transport, payloadChunker, converter, new Zipper(), chunkThreshold);

        } catch (Exception e) {

            throw new RuntimeException("Error initialising appender appenderExecutor", e);
        }
    }

    /**
     * Retrieves the localhost's hostname, or if unavailable, the ip address
     */
    private String getLocalHostName() throws SocketException, UnknownHostException {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            NetworkInterface networkInterface = NetworkInterface.getNetworkInterfaces().nextElement();
            if (networkInterface == null) throw e;
            InetAddress ipAddress = networkInterface.getInetAddresses().nextElement();
            if (ipAddress == null) throw e;
            return ipAddress.getHostAddress();
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
     * static additional fields to add to every gelf message. Key is the additional field key (and should thus begin
     * with an underscore). The value is a static string.
     */
    public Map<String, String> getStaticAdditionalFields() {
        return staticAdditionalFields;
    }

    public void setStaticAdditionalFields(Map<String, String> staticAdditionalFields) {
        this.staticAdditionalFields = staticAdditionalFields;
    }

    /**
     * Indicates if all values from the MDC should be included in the gelf
     * message or only the once listed as {@link #getAdditionalFields()
     * additional fields}.
     * <p>
     * If <code>true</code>, the gelf message will contain all values available
     * in the MDC. Each MDC key will be converted to a gelf custom field by
     * adding an underscore prefix. If an entry exists in
     * {@link #getAdditionalFields() additional field} it will be used instead.
     * </p>
     * <p>
     * If <code>false</code>, only the fields listed in
     * {@link #getAdditionalFields() additional field} will be included in the
     * message.
     * </p>
     *
     * @return the includeFullMDC
     */
    public boolean isIncludeFullMDC() {
        return includeFullMDC;
    }
    public void setIncludeFullMDC(boolean includeFullMDC) {
        this.includeFullMDC = includeFullMDC;
    }

    /**
     * Add an additional field. This is mainly here for compatibility with logback.xml
     *
     * @param keyValue This must be in format key:value where key is the MDC key, and value is the GELF field
     *                 name. e.g "ipAddress:_ip_address"
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
     * Add a staticAdditional field. This is mainly here for compatibility with logback.xml
     *
     * @param keyValue This must be in format key:value where key is the additional field key, and value is a static
     *                 string. e.g "_node_name:www013"
     */
    public void addStaticAdditionalField(String keyValue) {
        String[] splitted = keyValue.split(":");

        if (splitted.length != 2) {

            throw new IllegalArgumentException("staticAdditionalField must be of the format key:value, where key is the "
                    + "additional field key (therefore should have a leading underscore), and value is a static string. " +
                    "e.g. _node_name:www013");
        }

        staticAdditionalFields.put(splitted[0], splitted[1]);
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

    public String getMessagePattern() {
        return messagePattern;
    }

    public void setMessagePattern(String messagePattern) {
        this.messagePattern = messagePattern;
    }
}
