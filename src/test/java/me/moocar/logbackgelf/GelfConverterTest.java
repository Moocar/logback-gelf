package me.moocar.logbackgelf;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
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

    @Test
    public void testNumericConversion() throws NoSuchMethodException {
        Map<String, String> fieldTypes =
            ImmutableMap.<String, String>builder()
                .put("_intField", "int")
                .put("_IntegerField", "Integer")
                .put("_longField", "long").put("_LongField", "Long")
                .put("_floatField", "float").put("_FloatField", "Float")
                .put("_doubleField", "double")
                .put("_DoubleField", "Double").put("_foo", "foo")
            .build();

        MDC.put("intField", "123");
        MDC.put("IntegerField", "456");
        MDC.put("longField", Long.toString((long) Integer.MAX_VALUE + 1));
        MDC.put("LongField", Long.toString((long) Integer.MAX_VALUE + 2));
        MDC.put("floatField", "3.14159");
        MDC.put("FloatField", "2.71828");
        MDC.put("doubleField", "3E-50");
        MDC.put("DoubleField", "3E50");
        MDC.put("foo", "bar");

        LoggingEvent event = new LoggingEvent("my.class.name", logger, Level.INFO, "hello",  null, null);

        GelfConverter converter;
        Map<String, Object> additionalFields;

        // test including full MDC
        converter = new GelfConverter("facilty", false, false, false,
                new HashMap<String, String>(), fieldTypes,
                new HashMap<String, String>(), 10, "host", "%m%rEx", null,
                true);

        additionalFields = new HashMap<String, Object>();
        converter.additionalFields(additionalFields, event);
        validateFieldTypes(additionalFields);

        // test without including full MDC
        converter = new GelfConverter("facilty", false, false, false,
                new HashMap<String, String>(), fieldTypes,
                new HashMap<String, String>(), 10, "host", "%m%rEx", null,
                false);
        converter.additionalFields(additionalFields, event);
        validateFieldTypes(additionalFields);
    }

    private void validateFieldTypes(Map<String, Object> fields) {
        assertEquals(123, ((Integer) fields.get("_intField")).intValue());
        assertEquals(456, ((Integer) fields.get("_IntegerField")).intValue());
        assertEquals((long) Integer.MAX_VALUE + 1, ((Long) fields.get("_longField")).longValue());
        assertEquals((long) Integer.MAX_VALUE + 2, ((Long) fields.get("_LongField")).longValue());
        assertEquals((float) 3.14159, ((Float) fields.get("_floatField")).floatValue(), 0);
        assertEquals((float) 2.71828, ((Float) fields.get("_FloatField")).floatValue(), 0);
        assertEquals((double) 3e-50, ((Double) fields.get("_doubleField")).doubleValue(), 0);
        assertEquals((double) 3e50, ((Double) fields.get("_DoubleField")).doubleValue(), 0);
        assertEquals("bar", ((String) fields.get("_foo")));
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
        return new GelfConverter("facilty", false, false, useMarker, new HashMap<String, String>(), new HashMap<String, String>(), new HashMap<String, String>(), 10, "host", "%m%rEx", null, false);
    }

}
