package org.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.util.LevelToSyslogSeverity;
import ch.qos.logback.core.LayoutBase;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for formatting a log event into a GELF message
 */
public class GelfConverter<E> {

    private final String facility;
    private final boolean useLoggerName;
    private final Map<String, String> additionalFields;
    private final int shortMessageLength;

    private final Gson gson;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public GelfConverter(String facility,
                         boolean useLoggerName,
                         Map<String, String> additionalFields,
                         int shortMessageLength) {

        this.facility = facility;
        this.useLoggerName = useLoggerName;
        this.additionalFields = additionalFields;
        this.shortMessageLength = shortMessageLength;

        // Init GSON for underscores
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        this.gson = gsonBuilder.create();
    }

    public String convertToGelf(E event) {
        try {
            return gson.toJson(createMessage(event));
        } catch (RuntimeException e) {
            logger.error("Error creating JSON message", e);
            throw e;
        }
    }

    /**
     * Creates a map of properties that represent the GELF message.
     *
     * @param event The log event
     * @return map of gelf properties
     */
    private Map<String, Object> createMessage(E event) {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put("facility", facility);

        map.put("host", getHostname());

        ILoggingEvent eventObject = (ILoggingEvent) event;

        String message = eventObject.getFormattedMessage();

        IThrowableProxy proxy = eventObject.getThrowableProxy();
        if (proxy != null) {
            map.put("full_message", truncateToLongMessage(message + "\n" + proxy.getClassName() + ": " + proxy.
                    getMessage() + "\n" + toStackTraceString(proxy.getStackTraceElementProxyArray())));
            map.put("short_message", truncateToShortMessage(message + ", " + proxy.getClassName() + ": " + proxy.
                    getMessage()));
        } else {
            map.put("full_message", truncateToLongMessage(message));
            map.put("short_message", truncateToShortMessage(message));
        }

        map.put("timestamp", System.currentTimeMillis());
        map.put("version", "1.0");
        map.put("level", LevelToSyslogSeverity.convert(eventObject));

        additionalFields(map, eventObject);

        return map;
    }

    private String toStackTraceString(StackTraceElementProxy[] elements) {
        StringBuilder str = new StringBuilder();
        for (StackTraceElementProxy element : elements) {
            str.append(element.getSTEAsString());
        }
        return str.toString();
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void additionalFields(Map<String, Object> map, ILoggingEvent eventObject) {

        if (useLoggerName) {
            map.put("_loggerName", eventObject.getLoggerName());
        }

        Map<String, String> mdc = eventObject.getMDCPropertyMap();

        if (mdc != null) {

            for (String key : additionalFields.keySet()) {
                String field = mdc.get(key);
                if (field != null) {
                    map.put(additionalFields.get(key), field);
                }
            }

        }

    }

    private String truncateToShortMessage(String fullMessage) {
        if (fullMessage.length() > shortMessageLength) {
            return fullMessage.substring(0, shortMessageLength);
        }
        return fullMessage;
    }

    private String truncateToLongMessage(String fullMessage) {
        if (fullMessage.length() > 4096) {
            return fullMessage.substring(0, 4096);
        }
        return fullMessage;
    }
}
