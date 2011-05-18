import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import org.logbackgelf.GelfAppender

import static ch.qos.logback.classic.Level.ERROR
import static ch.qos.logback.classic.Level.DEBUG
import static ch.qos.logback.classic.Level.WARN

appender("GELF", GelfAppender) {
    facility = "logback-gelf-test"
    hostname = "localhost"
    port = 12201
    useLoggerName = true
    additionalFields = [ipAddress:"_ip_address"]
}

root(DEBUG, ["GELF"])