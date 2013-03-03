package me.moocar.logbackgelf;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.corba.se.impl.orb.ParserTable;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IntegrationTest {

    private static final String longMessage = createLongMessage();
    private TestServer server;
    private String ipAddress;
    private String requestID;
    private String host;
    private ImmutableSet<String> jsonKeys = ImmutableSet.of("host", "_ip_address", "_request_id", "facility", "full_message", "short_message",
            "_loggerName", "version");

    private static String createLongMessage() {
        Random rand = new Random();
        StringBuilder str = new StringBuilder();
        for (int i=0; i< 1000; i++) {
            char theChar = (char)(rand.nextInt(30) + 65);
            for (int j=0; j < 80; j++) {
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
        ipAddress = "87.345.23.55";
        MDC.put("ipAddress", ipAddress);
        requestID = String.valueOf(new Random().nextInt(100000));
        MDC.put("requestId", requestID);
        host = getLocalHostName();
    }

    @Test
    public void test() throws IOException {

        Logger logger = LoggerFactory.getLogger(this.getClass());

        logger.debug("this is a new test");
        sleep();
        ImmutableMap<String, String> lastRequest = server.lastRequest();
        assertMapEquals(makeMap("this is a new test"), removeFields(lastRequest));
        assertTrue(lastRequest.containsKey("level"));
        assertTrue(lastRequest.containsKey("timestamp"));

        logger.debug("this is a test with ({}) parameter", "this");
        sleep();
        lastRequest = server.lastRequest();
        assertMapEquals(makeMap("this is a test with (this) parameter"), removeFields(lastRequest));
        assertTrue(lastRequest.containsKey("level"));
        assertTrue(lastRequest.containsKey("timestamp"));

        try {
            new URL("app://asdfs");
        } catch (Exception e) {
            logger.error("expected error", new IllegalStateException(e));
            sleep();
            lastRequest = server.lastRequest();
            assertMapEquals(makeErrorMap(
                    "expected errorjava.net.MalformedURLException: unknown protocol: app\n" +
                            "\tat java.net.URL.<init>(URL.java:574) ~[na:1.6.0_41]\n" +
                            "\tat java.net.URL.<init>(URL.java:464) ~[na:1.6.0_41]\n" +
                            "\tat java.net.URL.<init>(URL.java:413) ~[na:1.6.0_41]\n" +
                            "\tat me.moocar.logbackgelf.In"), ImmutableMap.copyOf(Maps.filterKeys(removeFields(lastRequest), Predicates.not(Predicates.in(ImmutableSet.of("full_message"))))));
        }
        server.shutdown();
        logger.debug("This is a test with a really long ending: " + longMessage);
    }

    private void assertMapEquals (ImmutableMap<String, String> m1, ImmutableMap<String, String> m2) {
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

    private ImmutableMap<String, String> makeMap(String message) {
        return makeMap(message, message);
    }

    private ImmutableMap<String, String> makeMap(String fullMessage, String shortMessage) {
        return ImmutableMap.<String, String>builder()
                .put("_ip_address", ipAddress)
                .put("_request_id", requestID)
                .put("host", host)
                .put("facility", "logback-gelf-test")
                .put("short_message", shortMessage)
                .put("full_message", fullMessage)
                .put("_loggerName", "me.moocar.logbackgelf.IntegrationTest")
                .put("version", "1.0").build();
    }

    private ImmutableMap<String, String> removeFields(ImmutableMap<String, String> map) {
        return ImmutableMap.copyOf(Maps.filterKeys(map,Predicates.in(jsonKeys)));
    }

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private String getLocalHostName() throws SocketException, UnknownHostException {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            NetworkInterface networkInterface = NetworkInterface.getNetworkInterfaces().nextElement();
            if (networkInterface == null) throw e;
            InetAddress ipAddress = networkInterface.getInetAddresses().nextElement();
            if (ipAddress == null) throw e;
            return ipAddress.getHostAddress();
        }
    }

}
