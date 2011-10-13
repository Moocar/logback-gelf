package org.logbackgelf;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class MessageIdProvider {

    private final int messageIdLength;

    public MessageIdProvider(int message_id_length) {
        messageIdLength = message_id_length;
    }

    /**
     * Creates a message id that should be unique on every call. The message ID needs to be unique for every message. If
     * a message is chunked, then each chunk in a message needs the same message ID.
     *
     * @return unique message ID
     */
    public byte[] get() {
        try {
            // Uniqueness is guaranteed by combining the hostname and the current nano second, hashing the result, and
            // selecting the first 32 bytes of the result
            String hostname = InetAddress.getLocalHost().getHostName();
            String timestamp = String.valueOf(System.nanoTime());

            byte[] digestString = (hostname + timestamp).getBytes();

            try {
                MessageDigest digest = MessageDigest.getInstance("MD5");

                return Arrays.copyOf(digest.digest(digestString), messageIdLength);
            } catch (NoSuchAlgorithmException e) {

                throw new IllegalStateException("Could not get handle on MD5 Digest for creating chunk message ID", e);
            }
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Cannot find hostname for creating chunk message ID", e);
        }
    }
}
