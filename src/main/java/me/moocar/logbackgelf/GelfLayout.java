package me.moocar.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;

import java.util.Map;

public class GelfLayout extends LayoutBase<ILoggingEvent> {
    private GelfConverter converter;


    public GelfLayout(String facility,
                      boolean useLoggerName,
                      boolean useThreadName,
                      Map<String, String> additionalFields,
                      Map<String, String> staticAdditionalFields,
                      int shortMessageLength,
                      String hostname,
                      String messagePattern,
                      String shortMessagePattern,
                      boolean includeFullMDC) {
        this.converter = new GelfConverter(facility, useLoggerName, useThreadName, additionalFields, staticAdditionalFields, shortMessageLength, hostname, messagePattern, shortMessagePattern, includeFullMDC);
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
        return converter.toGelf(event);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }
}
