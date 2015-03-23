package me.moocar.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import static me.moocar.logbackgelf.InternetUtils.getInetAddress;
import static me.moocar.logbackgelf.InternetUtils.getLocalHostName;

/**
 * Responsible for Formatting a log event and sending it to a Graylog2 Server. Note that you can't swap in a different
 * Layout since the GELF format is static.
 */
public class GelfAppender<E extends ILoggingEvent> extends GelfLayout<E> {


}
