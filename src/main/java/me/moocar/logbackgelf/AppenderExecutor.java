package me.moocar.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;

public interface AppenderExecutor {
    void append(ILoggingEvent logEvent);
}
