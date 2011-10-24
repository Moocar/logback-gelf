package me.moocar.logbackgelf;

import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Responsible for creating a unique Gelf messageId
 */
public class MessageIdProvider {

    private final int messageIdLength;
    private final MessageDigest messageDigest;
    private final String hostname;

    public MessageIdProvider(int message_id_length, MessageDigest messageDigest, String hostname) {
        messageIdLength = message_id_length;
        this.messageDigest = messageDigest;
        this.hostname = hostname;
    }

    /**
     * Creates a message id that should be unique on every call. The message ID needs to be unique for every message. If
     * a message is chunked, then each chunk in a message needs the same message ID.
     *
     * @return unique message ID
     */
    public byte[] get() {

        // Uniqueness is guaranteed by combining the hostname and the current nano second, hashing the result, and
        // selecting the first x bytes of the result
        String timestamp = String.valueOf(System.nanoTime());

        byte[] digestString = (hostname + timestamp).getBytes();

        return Arrays.copyOf(messageDigest.digest(digestString), messageIdLength);
    }
}
