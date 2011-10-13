package org.logbackgelf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class Executor<E> {

    private final Transport transport;
    private final PayloadChunker payloadChunker;
    private final GelfConverter gelfConverter;
    private final int chunkThreshold;

    public Executor(Transport transport, PayloadChunker payloadChunker, GelfConverter gelfConverter,
                    int chunkThreshold) {
        this.transport = transport;
        this.payloadChunker = payloadChunker;
        this.gelfConverter = gelfConverter;
        this.chunkThreshold = chunkThreshold;
    }

    /**
     * The main append method. Takes the event that is being logged, formats if for GELF and then sends it over the wire
     * to the log server
     *
     * @param logEvent The event that we are logging
     */
    public void append(E logEvent) {

        byte[] payload = gzipString(gelfConverter.toGelf(logEvent));

        if (payload.length < chunkThreshold) {
            transport.send(payload);
        } else {
            transport.send(payloadChunker.chunkIt(payload));
        }
    }

    /**
     * zips up a string into a GZIP format.
     *
     * @param str The string to zip
     * @return The zipped string
     */
    private byte[] gzipString(String str) {
        GZIPOutputStream zipStream = null;
        try {
            ByteArrayOutputStream targetStream = new ByteArrayOutputStream();
            zipStream = new GZIPOutputStream(targetStream);
            zipStream.write(str.getBytes());
            zipStream.close();
            byte[] zipped = targetStream.toByteArray();
            targetStream.close();
            return zipped;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            try {
                if (zipStream != null) {
                    zipStream.close();
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
