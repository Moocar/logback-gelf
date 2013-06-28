package me.moocar.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;

public interface Transport {
	public void send(byte[] data, ILoggingEvent event);
}
