package me.moocar.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;

public class AMQPGelfAppender extends BaseAppender {
    private AMQPTransport transport;
    private GelfConverter converter;
    private Zipper zipper = new Zipper();

    private String exchangeName;
    private String routingKey;
    private int maxRetries = 0;
    private String graylog2AmqpUri;

    @Override
    protected void append(ILoggingEvent logEvent) {
        try {
            byte[] message = zipper.zip(converter.toGelf(logEvent));
            transport.send(message, logEvent);
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

            String hostname = getLocalHostName();
            converter = new GelfConverter(facility, useLoggerName, useThreadName, additionalFields,
                    staticAdditionalFields, shortMessageLength, hostname, messagePattern, shortMessagePattern,
                    includeFullMDC);
            transport = new AMQPTransport(graylog2AmqpUri, exchangeName, routingKey, maxRetries);
        } catch (Exception e) {
            throw new RuntimeException("Error initialising appender", e);
        }
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getGraylog2AmqpUri() {
        return graylog2AmqpUri;
    }

    public void setGraylog2AmqpUri(String graylog2AmqpUri) {
        this.graylog2AmqpUri = graylog2AmqpUri;
    }
}
