package org.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;
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

public class GelfLayout<E> extends LayoutBase<E> {

    private final Gson gson;
    private String facility;
    private boolean useLoggerName;
    private Map<String, String> additionalFields;
    private int shortMessageLength;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public String getFacility() {
        return facility;
    }

    public void setFacility(String facility) {
        this.facility = facility;
    }

    public boolean isUseLoggerName() {
        return useLoggerName;
    }

    public void setUseLoggerName(boolean useLoggerName) {
        this.useLoggerName = useLoggerName;
    }

    public Map<String, String> getAdditionalFields() {
        return additionalFields;
    }

    public void setAdditionalFields(Map<String, String> additionalFields) {
        this.additionalFields = additionalFields;
    }

    public int getShortMessageLength() {
        return shortMessageLength;
    }

    public void setShortMessageLength(int shortMessageLength) {
        this.shortMessageLength = shortMessageLength;
    }

    public GelfLayout() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        this.gson = gsonBuilder.create();
    }

    @Override
    public String doLayout(E event) {
        try {
            return getJson(createMessage(event));
        } catch (RuntimeException e) {
            logger.error("Error creating JSON message", e);
            throw e;
        }
    }

    private Map<String, Object> createMessage(E event) {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put("facility", facility);

        map.put("host", getHostname());

        ILoggingEvent eventObject = (ILoggingEvent) event;

        String fullMessage = eventObject.getMessage();

        map.put("short_message", truncateToShortMessage(fullMessage));
        map.put("full_message", fullMessage);
        map.put("timestamp", System.currentTimeMillis());
        map.put("version", "1.0");
        map.put("level", LevelToSyslogSeverity.convert(eventObject));

        additionalFields(map, eventObject);

        return map;
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

    private String getJson(Map<String, Object> gelfMessage) {
        return gson.toJson(gelfMessage);
    }

    private String truncateToShortMessage(String fullMessage) {
        if (fullMessage.length() > shortMessageLength) {
            return fullMessage.substring(0, shortMessageLength);
        }
        return fullMessage;
    }
}
