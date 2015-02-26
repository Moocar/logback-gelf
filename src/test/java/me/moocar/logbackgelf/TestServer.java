package me.moocar.logbackgelf;

import com.google.common.collect.ImmutableMap;

public interface TestServer {
    void start();

    ImmutableMap<String,Object> lastRequest();

    void shutdown();
}
