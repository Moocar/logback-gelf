import me.moocar.logbackgelf.GelfAppender

import static ch.qos.logback.classic.Level.DEBUG

appender("GELF", GelfAppender) {
    facility = "logback-gelf-test"
    graylog2ServerHost = "localhost"
    graylog2ServerPort = 12201
    useLoggerName = true
    graylog2ServerVersion = "0.9.6"
    chunkThreshold = 1000
    additionalFields = [ipAddress:"_ip_address", requestId:"_request_id"]
}

root(DEBUG, ["GELF"])