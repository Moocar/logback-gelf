package me.moocar.logbackgelf;

import java.lang.reflect.Method;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.util.LevelToSyslogSeverity;

import ch.qos.logback.core.Layout;
import ch.qos.logback.core.LayoutBase;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Responsible for formatting a log event into a GELF message
 */
public class GelfLayout<E extends ILoggingEvent> extends LayoutBase<E> {

    private final String DEFAULT_FULL_MESSAGE_PATTERN = "%rEx%m";
    private final String DEFAULT_SHORT_MESSAGE_PATTERN = "%ex{short}%.100m";

    private String facility = "GELF";
    private boolean useLoggerName = false;
    private boolean useThreadName = false;
    private boolean useMarker = false;
    private Map<String, String> additionalFields = new HashMap<String, String>();
    private Map<String, String> fieldTypes = new HashMap<String, String>();
    private Map<String, String> staticAdditionalFields = new HashMap<String, String>();
    private int shortMessageLength = 255;
    private String host = getLocalHostName();
    private final Gson gson;
    private Layout fullMessageLayout;
    private Layout shortMessageLayout;
    private boolean includeFullMDC = false;

    static Map<String, Method> primitiveTypes;

    static {
        primitiveTypes = new HashMap<String, Method>();
        try {
            primitiveTypes.put("int", Integer.class.getDeclaredMethod("parseInt", String.class));
            primitiveTypes.put("Integer", Integer.class.getDeclaredMethod("parseInt", String.class));
            primitiveTypes.put("long", Long.class.getDeclaredMethod("parseLong", String.class));
            primitiveTypes.put("Long", Long.class.getDeclaredMethod("parseLong", String.class));
            primitiveTypes.put("float", Float.class.getDeclaredMethod("parseFloat", String.class));
            primitiveTypes.put("Float", Float.class.getDeclaredMethod("parseFloat", String.class));
            primitiveTypes.put("double", Double.class.getDeclaredMethod("parseDouble", String.class));
            primitiveTypes.put("Double", Double.class.getDeclaredMethod("parseDouble", String.class));
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public GelfLayout() {

        this.additionalFields = new HashMap<String, String>();
        this.fieldTypes = new HashMap<String, String>();
        this.staticAdditionalFields = new HashMap<String, String>();

        // Init GSON for underscores
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        this.gson = gsonBuilder.create();
    }

    public void start() {
        if (isStarted()) return;
        int errorCount = 0;

        if (fullMessageLayout == null) {
            PatternLayout layout = new PatternLayout();
            layout.setPattern(DEFAULT_FULL_MESSAGE_PATTERN);
            layout.setContext(this.getContext());
            layout.start();
            this.fullMessageLayout = layout;
        }

        if (shortMessageLayout == null) {
            PatternLayout layout = new PatternLayout();
            layout.setPattern(DEFAULT_SHORT_MESSAGE_PATTERN);
            layout.setContext(this.getContext());
            layout.start();
            this.shortMessageLayout = layout;
        }

        if (errorCount == 0) {
            super.start();
        }
    }

    @Override
    public String doLayout(E event) {
        addInfo("encoding");
        Map<String, Object> map = mapFields(event);
        addInfo("created" + map);
        String jsonString = gson.toJson(map);
        addInfo("json size" + jsonString.getBytes(Charset.forName("UTF-8")).length);
        return jsonString;
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

        map.put("host", host);

        String message = fullMessageLayout.doLayout(logEvent);

        map.put("full_message", message);
        map.put("short_message", truncateToShortMessage(message, logEvent));

        // Ever since version 0.9.6, GELF accepts timestamps in decimal form.
        double logEventTimeTimeStamp = logEvent.getTimeStamp() / 1000.0;

        stackTraceField(map, logEvent);

        map.put("timestamp", logEventTimeTimeStamp);

        map.put("version", "1.1");

        map.put("level", LevelToSyslogSeverity.convert(logEvent));

        additionalFields(map, logEvent);

        staticAdditionalFields(map);

        return map;
    }

    private void stackTraceField(Map<String, Object> map, ILoggingEvent eventObject) {
        IThrowableProxy throwableProxy = eventObject.getThrowableProxy();
        if (throwableProxy != null ) {
            StackTraceElementProxy[] proxyStackTraces = throwableProxy.getStackTraceElementProxyArray();
            if (proxyStackTraces != null && proxyStackTraces.length > 0) {
                StackTraceElement[] callStackTraces = eventObject.getCallerData();
                if (callStackTraces != null && callStackTraces.length > 0) {
                    StackTraceElement lastStack = callStackTraces[0];
                    map.put("file", lastStack.getFileName());
                    map.put("line", String.valueOf(lastStack.getLineNumber()));
                }
            }
        }
    }

    /**
     * Converts the additional fields into proper GELF JSON
     *
     * @param map         The map of additional fields
     * @param eventObject The Logging event that we are converting to GELF
     */
    /* allow testing */ void additionalFields(Map<String, Object> map, ILoggingEvent eventObject) {

        if (useLoggerName) {
            map.put("_loggerName", eventObject.getLoggerName());
        }

        if(useMarker && eventHasMarker(eventObject)) {
            map.put("_marker", eventObject.getMarker().toString());
        }

        if (useThreadName) {
            map.put("_threadName", eventObject.getThreadName());
        }

        Map<String, String> mdc = eventObject.getMDCPropertyMap();

        if (mdc != null) {

            if (includeFullMDC) {
                for (Entry<String, String> e : mdc.entrySet()) {
                    if (additionalFields.containsKey(e.getKey())) {
                        map.put(additionalFields.get(e.getKey()), convertFieldType(e.getValue(), additionalFields.get(e.getKey())));
                    } else {
                        map.put("_" + e.getKey(), convertFieldType(e.getValue(), "_" + e.getKey()));
                    }
                }
            } else {
                for (String key : additionalFields.keySet()) {
                    String field = mdc.get(key);
                    if (field != null) {
                        map.put(additionalFields.get(key), convertFieldType(field, key));
                    }
                }
            }
        }
    }

    private Object convertFieldType(Object value, final String type) {
        if (primitiveTypes.containsKey(fieldTypes.get(type))) {
            try {
                value = primitiveTypes.get(fieldTypes.get(type)).invoke(null,
                        value);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
        return value;
    }

    private boolean eventHasMarker(ILoggingEvent eventObject) {
        return eventObject.getMarker() != null;
    }

    private void staticAdditionalFields(Map<String,Object> map) {

        for (String key : staticAdditionalFields.keySet()) {
            map.put(key, (staticAdditionalFields.get(key)));
        }
    }

    private String truncateToShortMessage(String fullMessage, ILoggingEvent logEvent) {
        if ( shortMessageLayout != null ) {
            return shortMessageLayout.doLayout(logEvent);
        }

        if (fullMessage.length() > shortMessageLength) {
            return fullMessage.substring(0, shortMessageLength);
        }
        return fullMessage;
    }

    private String getLocalHostName() {
        try {
            return InternetUtils.getLocalHostName();
        } catch (SocketException e) {
            return "UNKNOWN";
        } catch (UnknownHostException e) {
            return "UNKNOWN";
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
     * If true, an additional field call "_loggerName" will be added to each gelf message. Its contents will be the
     * fully qualified name of the logger. e.g: com.company.Thingo.
     */
    public boolean isUseLoggerName() {
        return useLoggerName;
    }

    public void setUseLoggerName(boolean useLoggerName) {
        this.useLoggerName = useLoggerName;
    }

    public boolean isUseMarker() {
        return useMarker;
    }

    public void setUseMarker(boolean useMarker) {
        this.useMarker = useMarker;
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
     * Override the local host using a config option
     * @return the local host (defaults to getLocalHost() if not overridden
     * in config
     */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
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

    public void addFieldType(String keyValue) {
        String[] splitted = keyValue.split(":");

        if (splitted.length != 2 ||
                !GelfLayout.primitiveTypes.containsKey(splitted[1])) {
            throw new IllegalArgumentException(
                    "fieldType must be of the format key:value, where key is the " +
                            "field key, and value is the type to convert to (one of " +
                            GelfLayout.primitiveTypes.keySet() +
                            ")");
        }

        fieldTypes.put(splitted[0], splitted[1]);

    }

    public Map<String, String> getFieldTypes() {
        return fieldTypes;
    }

    public void setFieldTypes(final Map<String, String> fieldTypes) {
        this.fieldTypes = fieldTypes;
    }

    public Layout getFullMessageLayout() {
        return fullMessageLayout;
    }

    public void setFullMessageLayout(Layout fullMessageLayout) {
        this.fullMessageLayout = fullMessageLayout;
    }

    public Layout getShortMessageLayout() {
        return shortMessageLayout;
    }

    public void setShortMessageLayout(Layout shortMessageLayout) {
        this.shortMessageLayout = shortMessageLayout;
    }
}
