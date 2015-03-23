package me.moocar.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.GZIPOutputStream;

/**
 * Created by anthony on 3/9/15.
 */
public class GelfEncoder<E extends ILoggingEvent> extends LayoutWrappingEncoder<E> {

    @Override
    public void init(OutputStream os) throws IOException {
        //super.init(new GZIPOutputStream(os));
        super.init(os);
    }
}
