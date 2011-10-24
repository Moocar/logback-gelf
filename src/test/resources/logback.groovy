import me.moocar.logbackgelf.GelfAppender

import static ch.qos.logback.classic.Level.DEBUG

appender("GELF", GelfAppender) {
    facility = "logback-gelf-test"
    graylog2ServerHost = "localhost"
    graylog2ServerPort = 12201
    useLoggerName = true
    graylog2ServerVersion = "0.9.5"
    chunkThreshold = 1000
    additionalFields = [ipAddress:"_ip_address"]
}

root(DEBUG, ["GELF"])