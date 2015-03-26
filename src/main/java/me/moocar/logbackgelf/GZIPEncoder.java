package me.moocar.logbackgelf;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.zip.GZIPOutputStream;

public class GZIPEncoder<E extends ILoggingEvent> extends LayoutWrappingEncoder<E> {

    public void doEncode(E event) throws IOException {
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
        String txt = layout.doLayout(event);
        byte[] bytes = txt.getBytes(Charset.forName("UTF-8"));
        gzipOutputStream.write(bytes);
        gzipOutputStream.finish();
        gzipOutputStream.flush();
        gzipOutputStream.close();
    }
}
