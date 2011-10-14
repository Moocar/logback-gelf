package org.logbackgelf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * Converts a log event into a a payload or chunks and sends them to the graylog2-server
 */
public class AppenderExecutor<E> {

    private final Transport transport;
    private final PayloadChunker payloadChunker;
    private final GelfConverter gelfConverter;
    private final int chunkThreshold;

    public AppenderExecutor(Transport transport,
                            PayloadChunker payloadChunker,
                            GelfConverter gelfConverter,
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

        // If we can fit all the information into one packet, then just send it
        if (payload.length < chunkThreshold) {

            transport.send(payload);

        // If the message is too long, then slice it up and send multiple packets
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
