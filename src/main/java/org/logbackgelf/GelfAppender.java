package org.logbackgelf;

import ch.qos.logback.core.AppenderBase;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class GelfAppender<E> extends AppenderBase<E> {

    // Defaults for variables up here
    private final GelfLayout<E> gelfLayout;
    private String facility = "GELF";
    private String graylog2ServerHost = "localhost";
    private int graylog2ServerPort = 12201;
    private boolean useLoggerName = false;
    private Map<String, String> additionalFields = new HashMap<String, String>();
    private int shortMessageLength = 255;

    public GelfAppender() {
        this.gelfLayout = new GelfLayout<E>();
    }

    /**
     * Facility
     * @return
     */
    public String getFacility() {
        return facility;
    }

    public void setFacility(String facility) {
        this.facility = facility;
    }

    /**
     * The hostname of the graylog2 server to connect to
     * @return
     */
    public String getGraylog2ServerHost() {
        return graylog2ServerHost;
    }

    public void setGraylog2ServerHost(String graylog2ServerHost) {
        this.graylog2ServerHost = graylog2ServerHost;
    }

    /**
     * The port of the graylog2 server to connect to
     * @return
     */
    public int getGraylog2ServerPort() {
        return graylog2ServerPort;
    }

    public void setGraylog2ServerPort(int graylog2ServerPort) {
        this.graylog2ServerPort = graylog2ServerPort;
    }

    /**
     * Specifies whether to add the additional field '_logger_name'. If true, then the appender will grab the loggerName from the
     * eventObject. _logger_name value will be something like com.company.package.Foo.
     * @return
     */
    public boolean isUseLoggerName() {
        return useLoggerName;
    }

    public void setUseLoggerName(boolean useLoggerName) {
        this.useLoggerName = useLoggerName;
    }

    /**
     * additional fields to add to the gelf message. Here's how these work:
     * <br/>
     * Let's take an example. I want to log the client's ip address of every request that comes into my web server. To do this,
     * I add the ipaddress to the slf4j MDC on each request as follows:
     * <code>
     * ...
     * MDC.put("ipAddress", "44.556.345.657");
     * ...
     * </code>
     * Now, to include the ip address in the gelf message, i just add the following to my logback.groovy:
     * <code>
     * appender("GELF", GelfAppender) {
     *   ...
     *   additionalFields = [identity:"_identity"]
     *   ...
     * }
     * </code>
     * in the additionalFields map, the key is the name of the MDC to look up. the value is the name that should be given to
     * the key in the additional field in the gelf message.
     *
     * @return
     */
    public Map<String, String> getAdditionalFields() {
        return additionalFields;
    }

    public void setAdditionalFields(Map<String, String> additionalFields) {
        this.additionalFields = additionalFields;
    }

    /**
     * The length of the message to truncate to
     * @return
     */
    public int getShortMessageLength() {
        return shortMessageLength;
    }

    public void setShortMessageLength(int shortMessageLength) {
        this.shortMessageLength = shortMessageLength;
    }

    @Override
    protected void append(E eventObject) {
        initDefaults();
        try {
            String message = gelfLayout.doLayout(eventObject);

            byte[] data = gzipMessage(message);

            send(data);

        } catch (RuntimeException e) {
            this.addError("Error occurred: ", e);
        }
    }

    /**
     * We have to forward on parameters to the GelfLayout. There may be a better way to do this.
     */
    private void initDefaults() {
        gelfLayout.setFacility(facility);
        gelfLayout.setUseLoggerName(useLoggerName);
        gelfLayout.setAdditionalFields(additionalFields);
        gelfLayout.setShortMessageLength(shortMessageLength);
    }

    private byte[] gzipMessage(String message) {
        GZIPOutputStream zipStream = null;
        try {
            ByteArrayOutputStream targetStream = new ByteArrayOutputStream();
            zipStream = new GZIPOutputStream(targetStream);
            zipStream.write(message.getBytes());
            zipStream.close();
            byte[] zipped = targetStream.toByteArray();
            targetStream.close();
            return zipped;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            try {
                zipStream.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void send(byte[] data) {
        InetAddress address = getInetAddress(graylog2ServerHost);
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, graylog2ServerPort);
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
