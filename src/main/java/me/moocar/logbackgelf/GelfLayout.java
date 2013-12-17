package me.moocar.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.LayoutBase;

import java.util.HashMap;
import java.util.Map;

import static me.moocar.logbackgelf.util.InternetUtils.getLocalHostName;

public class GelfLayout extends LayoutBase<ILoggingEvent> {
    // The following are configurable via logback configuration
    private String facility = "GELF";
    private boolean useLoggerName = false;
    private String hostName;
    private boolean useThreadName = false;
    private boolean useMarker = false;
    private boolean appendLineSeparator = false;
    private String messagePattern = "%m%rEx";
    private String shortMessagePattern = null;
    private Map<String, String> additionalFields = new HashMap<String, String>();
    private Map<String, String> staticAdditionalFields = new HashMap<String, String>();
    private boolean includeFullMDC;

    // The following are hidden (not configurable)
    private int shortMessageLength = 255;


    private GelfConverter converter;

    public boolean isAppendLineSeparator() {
        return appendLineSeparator;
    }

    public void setAppendLineSeparator(boolean appendLineSeparator) {
        this.appendLineSeparator = appendLineSeparator;
    }

    public boolean isUseMarker() {
        return useMarker;
    }

    public void setUseMarker(boolean useMarker) {
        this.useMarker = useMarker;
    }

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

    public String getHostName()
    {
        return hostName;
    }

    public void setHostName(final String hostName)
    {
        this.hostName = hostName;
    }

    public boolean isUseThreadName() {
        return useThreadName;
    }

    public void setUseThreadName(boolean useThreadName) {
        this.useThreadName = useThreadName;
    }

    public Map<String, String> getAdditionalFields() {
        return additionalFields;
    }

    public void setAdditionalFields(Map<String, String> additionalFields) {
        this.additionalFields = additionalFields;
    }

    public Map<String, String> getStaticAdditionalFields() {
        return staticAdditionalFields;
    }

    public void setStaticAdditionalFields(Map<String, String> staticAdditionalFields) {
        this.staticAdditionalFields = staticAdditionalFields;
    }

    public int getShortMessageLength() {
        return shortMessageLength;
    }

    public void setShortMessageLength(int shortMessageLength) {
        this.shortMessageLength = shortMessageLength;
    }

    public String getMessagePattern() {
        return messagePattern;
    }

    public void setMessagePattern(String messagePattern) {
        this.messagePattern = messagePattern;
    }

    public String getShortMessagePattern() {
        return shortMessagePattern;
    }

    public void setShortMessagePattern(String shortMessagePattern) {
        this.shortMessagePattern = shortMessagePattern;
    }

    public boolean isIncludeFullMDC() {
        return includeFullMDC;
    }

    public void setIncludeFullMDC(boolean includeFullMDC) {
        this.includeFullMDC = includeFullMDC;
    }


    /**
     * Transform an event (of type Object) and return it as a String after
     * appropriate formatting.
     * <p/>
     * <p>Taking in an object and returning a String is the least sophisticated
     * way of formatting events. However, it is remarkably CPU-effective.
     * </p>
     *
     * @param event The event to format
     * @return the event formatted as a String
     */
    @Override
    public String doLayout(ILoggingEvent event) {
        String eventString = converter.toGelf(event);

        return isAppendLineSeparator() ? eventString + CoreConstants.LINE_SEPARATOR : eventString;

    }

    @Override
    public void start() {
        super.start();

        createConverter();

    }

    private void createConverter() {
        try {
            if (hostName == null) {
                hostName = getLocalHostName();
            }

            this.converter = new GelfConverter(facility, useLoggerName, useThreadName, useMarker, additionalFields, staticAdditionalFields, shortMessageLength, hostName, messagePattern, shortMessagePattern, includeFullMDC);
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize converter", e);
        }
    }

    @Override
    public String getContentType() {
        return "application/json";
    }
}
