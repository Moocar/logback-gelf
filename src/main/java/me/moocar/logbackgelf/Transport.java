package me.moocar.logbackgelf;

import java.util.List;

public interface Transport {
    void send(byte[] data);

    void send(List<byte[]> packets);
}
