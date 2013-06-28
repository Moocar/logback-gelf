package me.moocar.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

/**
 * Responsible for sending amqp packet(s) to an amqp broker
 */
public class AMQPTransport implements Transport {

    private ConnectionFactory factory;
    private Channel channel;
    private String exchangeName;
    private String routingKey;
    private int maxRetries;

    public AMQPTransport(String graylog2AmqpUri, String exchangeName, String routingKey, Integer maxRetries) throws KeyManagementException, NoSuchAlgorithmException, URISyntaxException {
        factory = new ConnectionFactory();
        factory.setUri(graylog2AmqpUri);
		
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.maxRetries = maxRetries;
    }

    /**
     * Sends a single packet GELF message to an amqp broker
     *
     * @param data gzipped GELF Message (JSON)
     */
    public void send(byte[] data, ILoggingEvent event) {
        // set unique id to identify duplicates after connection failure
        String uuid = UUID.randomUUID().toString();
        String messageid = "gelf" + new Date().getTime() + uuid;

        int tries = 0;
        do {
            try {
                // establish the connection the first time
                if (channel == null) {
                    Connection connection = factory.newConnection();
                    channel = connection.createChannel();
                }

                BasicProperties.Builder propertiesBuilder = new BasicProperties.Builder();
                propertiesBuilder.contentType("application/json; charset=utf-8");
                propertiesBuilder.contentEncoding("gzip");
                propertiesBuilder.messageId(messageid);
                propertiesBuilder.timestamp(new Date(event.getTimeStamp()));
                BasicProperties properties = propertiesBuilder.build();

                channel.basicPublish(exchangeName, routingKey, properties, data);
                channel.waitForConfirms();

                return;
            } catch (Exception e) {
                channel = null;
                tries++;
            }
        } while (tries <= maxRetries || maxRetries < 0);
    }
}
