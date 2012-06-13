import me.moocar.logbackgelf.GelfAppender

import static ch.qos.logback.classic.Level.DEBUG

appender("GELF", GelfAppender) {
    facility = "logback-gelf-test"
    graylog2ServerHost = "adm-grbl00.sv.walmartlabs.com"
    graylog2ServerPort = 12201
    useLoggerName = true
    graylog2ServerVersion = "0.9.6"
    chunkThreshold = 1000
    additionalFields = [ipAddress:"_ip_address", requestId:"_request_id", contextName:"_context_name"]
}

root(DEBUG, ["GELF"])