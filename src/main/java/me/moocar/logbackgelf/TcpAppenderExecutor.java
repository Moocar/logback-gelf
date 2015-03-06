package me.moocar.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;

public class TcpAppenderExecutor implements AppenderExecutor {
    private final Transport transport;
    private final GelfConverter gelfConverter;

    public TcpAppenderExecutor(Transport transport, GelfConverter gelfConverter) {
        this.transport = transport;
        this.gelfConverter = gelfConverter;
    }

    @Override
    public void append(ILoggingEvent logEvent) {
        transport.send(gelfConverter.toGelf(logEvent).getBytes());
    }
}
