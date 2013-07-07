package me.moocar.logbackgelf;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.Test;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.HashMap;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


public class GelfConverterTest {
    private String markerString = "ImaMarker";

    private Marker marker = MarkerFactory.getMarker(markerString);
    private LoggerContext lc = new LoggerContext();
    private Logger logger = lc.getLogger("mine");

    @Test
    public void testNoMarkerWhenUseMarkerIsFalse() {
        testForMarker(false);
    }

    @Test
    public void testForMarkerWhenUseMarkerIsTrue() {
        testForMarker(true);
    }

    @Test
    public void testForNoMarkerWhenUseMarkerIsTrueButNoMarkerIsSet() {
        ILoggingEvent event = createEvent(false);
        GelfConverter converter = createGelfConverter(true);

        String gelfString = converter.toGelf(event);

        assertThat(gelfString.contains("\"_marker\""), is(equalTo(false)));
        assertThat(gelfString.contains(markerString), is(equalTo(false)));
    }

    private void testForMarker(boolean useMarker) {
        ILoggingEvent event = createEvent(useMarker);
        GelfConverter converter = createGelfConverter(useMarker);

        String gelfString = converter.toGelf(event);

        assertThat(gelfString.contains("\"_marker\""), is(equalTo(useMarker)));
        assertThat(gelfString.contains(markerString), is(equalTo(useMarker)));
    }

    private LoggingEvent createEvent(boolean addMarker) {
        LoggingEvent hello = new LoggingEvent("my.class.name", logger, Level.INFO, "hello", null, null);
        if (addMarker) {
            hello.setMarker(marker);
        }
        return hello;
    }

    private GelfConverter createGelfConverter(boolean useMarker) {
        return new GelfConverter("facilty", false, false, useMarker, new HashMap<String, String>(), new HashMap<String, String>(), 10, "host", "%m%rEx", null, false);
    }

}
