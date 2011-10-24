package me.moocar.logbackgelf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class Zipper {

    /**
     * zips up a string into a GZIP format.
     *
     * @param str The string to zip
     * @return The zipped string
     */
    public byte[] zip(String str) {
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
