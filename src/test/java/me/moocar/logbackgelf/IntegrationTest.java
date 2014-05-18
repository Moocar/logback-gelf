package me.moocar.logbackgelf;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IntegrationTest {

    private static final String longMessage = createLongMessage();
    private TestServer server;
    private String ipAddress;
    private String requestID;
    private String host;
    private ImmutableSet<Object> fieldsToIgnore = ImmutableSet.of((Object)"level", (Object)"timestamp");
    private ImmutableMap<String, Object> lastRequest = null;

    private static String createLongMessage() {
        Random rand = new Random();
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            char theChar = (char) (rand.nextInt(30) + 65);
            for (int j = 0; j < 80; j++) {
                str.append(theChar);
            }
            str.append('\n');
        }
        return str.toString();
    }

    @Before
    public void setup() throws SocketException, UnknownHostException {
        server = TestServer.build();
        server.start();
        host = "Test";
    }

    @Test
    public void test() throws IOException, JoranException {

        Logger logger = LoggerFactory.getLogger(this.getClass());
        MDC.clear();
        String message = "Testing empty MDC";

        // Basic Request
        logger.debug(message);
        sleep();
        lastRequest = server.lastRequest();
        assertMapEquals(makeMap(message), removeFields(lastRequest));
        assertTrue(lastRequest.containsKey("level"));
        assertTrue(lastRequest.containsKey("timestamp"));
        assertTrue(lastRequest.containsKey("host"));

        // Test with IP and requestID in MDC
        ipAddress = "87.345.23.55";
        MDC.put("ipAddress", ipAddress);
        requestID = String.valueOf(new Random().nextInt(100000));
        MDC.put("requestId", requestID);

        message = "this is a new test";
        logger.debug(message);
        sleep();
        lastRequest = server.lastRequest();
        assertMapEquals(makeMap(message), removeFields(lastRequest));
        assertTrue(lastRequest.containsKey("level"));
        assertTrue(lastRequest.containsKey("timestamp"));

        // Test substitution works
        logger.debug("this is a test with ({}) parameter", "this");
        sleep();
        lastRequest = server.lastRequest();
        assertMapEquals(makeMap("this is a test with (this) parameter"), removeFields(lastRequest));
        assertTrue(lastRequest.containsKey("level"));
        assertTrue(lastRequest.containsKey("timestamp"));

        // Test file and line are output for stack trace
        try {
            new URL("app://asdfs");
        } catch (Exception e) {
            logger.error("expected error", new IllegalStateException(e));
            sleep();
        }

        // Test field in MDC is added even if not included in additional fields
        MDC.put("newField", "the val");
        message = "this is a test with an MDC field (new_field) that is not included in the additional fields. " +
                "However includeFullMDC is set, so it should appear in the additional fields as _newField = the val";
        logger.debug(message, "this");
        sleep();
        lastRequest = server.lastRequest();
        assertMapEquals(addField(makeMap(message), "_newField", "the val"), removeFields(lastRequest));
        assertTrue(lastRequest.containsKey("level"));
        assertTrue(lastRequest.containsKey("timestamp"));

        // Test static additional field
        message = "Testing with a static additional field";
        MDC.clear();
        ipAddress = null;
        requestID = null;
        addStaticFieldToAppender();
        logger.debug(message);
        sleep();
        lastRequest = server.lastRequest();
        assertMapEquals(addField(makeMap(message, "Testing wi"), "_node_name", "www013"), removeFields(lastRequest));
        assertTrue(lastRequest.containsKey("level"));
        assertTrue(lastRequest.containsKey("timestamp"));

        // Test field type conversion
        addTypedFieldToAppender();
        int i = new Random().nextInt(100000);
        MDC.put("requestId", String.valueOf(i));
        message = "Testing requestId as int field conversion";
        logger.debug(message);
        sleep();
        lastRequest = server.lastRequest();
        assertTrue(lastRequest.get("_request_id").getClass().getSimpleName(), lastRequest.get("_request_id") instanceof Number);
        assertEquals(i, ((Number) lastRequest.get("_request_id")).intValue());

        // Test failed type conversion
        MDC.put("requestId", "nonparseablenumber");
        message = "Testing failed requestId as int field conversion";
        logger.debug(message);
        sleep();
        lastRequest = server.lastRequest();
        assertEquals("nonparseablenumber", lastRequest.get("_request_id"));

        // Finish
        server.shutdown();
        logger.debug("This is a test with a really long ending: " + longMessage);
    }

    private void addTypedFieldToAppender() throws JoranException {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator joranConfigurator = new JoranConfigurator();
        joranConfigurator.setContext(lc);
        joranConfigurator.doConfigure(Resources.getResource("typedFields.xml"));
    }

    private void addStaticFieldToAppender() throws JoranException {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator joranConfigurator = new JoranConfigurator();
        joranConfigurator.setContext(lc);
        joranConfigurator.doConfigure(Resources.getResource("staticAdditionalFields.xml"));
    }

    private ImmutableMap<String, Object> addField(ImmutableMap<String, Object> map, String key, String value) {
        return ImmutableMap.<String, Object>builder().putAll(map).put(key,
                value).build();
    }

    private void assertMapEquals(ImmutableMap<String, Object> m1, ImmutableMap<String, Object> m2) {
        //System.out.println(m1);
        //System.out.println(m2);
        assertTrue("Difference:" + Maps.difference(m1, m2).entriesDiffering(), Maps.difference(m1, m2).areEqual());
    }

    private ImmutableMap<String, String> makeErrorMap(String shortMessage) throws IOException {
        return ImmutableMap.<String, String>builder()
                .put("_ip_address", ipAddress)
                .put("_request_id", requestID)
                .put("host", host)
                .put("facility", "logback-gelf-test")
                .put("short_message", shortMessage)
                .put("_loggerName", "me.moocar.logbackgelf.IntegrationTest")
                .put("version", "1.0").build();
    }

    private ImmutableMap<String, Object> makeMap(String message) {
        return makeMap(message, message);
    }

    private ImmutableMap<String, Object> makeMap(String fullMessage, String shortMessage) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                .put("host", host)
                .put("facility", "logback-gelf-test")
                .put("short_message", shortMessage)
                .put("full_message", fullMessage)
                .put("_loggerName", "me.moocar.logbackgelf.IntegrationTest")
                .put("version", "1.0");
        if (ipAddress != null)
            builder.put("_ip_address", ipAddress);
        if (requestID != null)
            builder.put("_request_id", requestID);
        return builder.build();
    }

    private ImmutableMap<String, Object> removeFields(ImmutableMap<String, Object> map) {
        return ImmutableMap.copyOf(Maps.filterKeys(map, Predicates.not(Predicates.in(fieldsToIgnore))));
    }

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
