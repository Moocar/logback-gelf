package me.moocar.logbackgelf;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LevelToSyslogSeverity;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for formatting a log event into a GELF message
 */
public class GelfConverter<E> {

    private final String facility;
    private final boolean useLoggerName;
    private final boolean useThreadName;
    private final Map<String, String> additionalFields;
    private final int shortMessageLength;
    private final String hostname;
    private final Gson gson;
    private final PatternLayout patternLayout;

    public GelfConverter(String facility,
                         boolean useLoggerName,
                         boolean useThreadName,
                         Map<String, String> additionalFields,
                         int shortMessageLength,
                         String hostname,
                         String messagePattern) {

        this.facility = facility;
        this.useLoggerName = useLoggerName;
        this.useThreadName = useThreadName;
        this.additionalFields = additionalFields;
        this.shortMessageLength = shortMessageLength;
        this.hostname = hostname;

        // Init GSON for underscores
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        this.gson = gsonBuilder.create();
        this.patternLayout = new PatternLayout();
        this.patternLayout.setContext(new LoggerContext());
        this.patternLayout.setPattern(messagePattern);
        this.patternLayout.start();
    }

    /**
     * Converts a log event into GELF JSON.
     *
     * @param logEvent The log event we're converting
     * @return The log event converted into GELF JSON
     */
    public String toGelf(E logEvent) {
        try {
            return gson.toJson(mapFields(logEvent));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Error creating JSON message", e);
        }
    }

    /**
     * Creates a map of properties that represent the GELF message.
     *
     * @param logEvent The log event
     * @return map of gelf properties
     */
    private Map<String, Object> mapFields(E logEvent) {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put("facility", facility);

        map.put("host", hostname);

        ILoggingEvent eventObject = (ILoggingEvent) logEvent;

        String message = patternLayout.doLayout(eventObject);

        map.put("full_message", message);
        map.put("short_message", truncateToShortMessage(message));

        // Ever since version 0.9.6, GELF accepts timestamps in decimal form.
        double logEventTimeTimeStamp = eventObject.getTimeStamp() / 1000.0;

        stackTraceField(map, eventObject);

        map.put("timestamp", logEventTimeTimeStamp);

        map.put("version", "1.0");

        map.put("level", LevelToSyslogSeverity.convert(eventObject));

        additionalFields(map, eventObject);

        return map;
    }

    private void stackTraceField(Map<String, Object> map, ILoggingEvent eventObject) {
        StackTraceElement[] stackTrace = eventObject.getCallerData();
        if (stackTrace.length > 0) {
            StackTraceElement lastStack = stackTrace[0];
            StringBuffer sb = new StringBuffer();
            sb.append(lastStack.getFileName()).append(":").append(lastStack.getLineNumber());
            map.put("file", sb.toString());
        }
    }

    /**
     * Converts the additional fields into proper GELF JSON
     *
     * @param map         The map of additional fields
     * @param eventObject The Logging event that we are converting to GELF
     */
    private void additionalFields(Map<String, Object> map, ILoggingEvent eventObject) {

        if (useLoggerName) {
            map.put("_loggerName", eventObject.getLoggerName());
        }

        if (useThreadName) {
            map.put("_threadName", eventObject.getThreadName());
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
}
