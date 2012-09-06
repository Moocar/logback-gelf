LOGBACK-GELF - A GELF Appender for Logback
==========================================

Use this appender to log messages with logback to a Graylog2 server via GELF messages. Supports Additional Fields and chunking.

If you don't know what [Graylog2](http://graylog2.org) is, jump on the band wagon!

Installation
-----------------------------------

Simply add logback-gelf to your classpath. Either
[download the jar](https://github.com/Moocar/logback-gelf/downloads)
or if you're in [maven](http://mvnrepository.com/artifact/me.moocar/logback-gelf) land, the dependency details are below.

        <dependencies>
            ...
            <dependency>
                <groupId>me.moocar</groupId>
                <artifactId>logback-gelf</artifactId>
                <version>0.9.6p2</version>
            </dependency>
            ...
        </dependencies>

Configuring Logback
---------------------

Add the following to your logback.xml configuration file.

[src/main/resources/logback.xml](https://github.com/Moocar/logback-gelf/blob/master/src/test/resources/logback.xml)

    <configuration>
        <appender name="GELF" class="me.moocar.logbackgelf.GelfAppender">
            <facility>logback-gelf-test</facility>
            <graylog2ServerHost>localhost</graylog2ServerHost>
            <graylog2ServerPort>12201</graylog2ServerPort>
            <useLoggerName>true</useLoggerName>
            <useThreadName>true</useThreadName>
            <graylog2ServerVersion>0.9.6</graylog2ServerVersion>
            <chunkThreshold>1000</chunkThreshold>
            <messagePattern>%m%rEx</messagePattern>
            <additionalField>ipAddress:_ip_address</additionalField>
            <additionalField>requestId:_request_id</additionalField>
        </appender>

        <root level="debug">
            <appender-ref ref="GELF" />
        </root>
    </configuration>

If you're using groovy configuration, checkout the
[logback.groovy](https://github.com/Moocar/logback-gelf/blob/master/sample.logback.groovy) example.

Properties
----------

*   **facility**: The name of your service. Appears in facility column in graylog2-web-interface. Defaults to "GELF"
*   **graylog2ServerHost**: The hostname of the graylog2 server to send messages to. Defaults to "localhost"
*   **graylog2ServerPort**: The graylog2ServerPort of the graylog2 server to send messages to. Defaults to 12201
*   **useLoggerName**: If true, an additional field call "_loggerName" will be added to each gelf message. Its contents
will be the fully qualified name of the logger. e.g: com.company.Thingo. Defaults to false;
*   **useThreadName**: If true, an additional field call "_threadName" will be added to each gelf message. Its contents
will be the name of the thread. Defaults to false;
*   **graylog2ServerVersion**: Specify which version the graylog2-server is. This is important because the GELF headers
changed from 0.9.5 -> 0.9.6. Allowed values = 0.9.5 and 0.9.6. Defaults to "0.9.6"
*   **chunkThreshold**: The maximum number of bytes allowed by the payload before the message should be chunked into
smaller packets. Defaults to 1000
*   **messagePattern**: The layout of the actual message according to
[PatternLayout](http://logback.qos.ch/manual/layouts.html#conversionWord). Defaults to "%m%rEx"
*   **additionalFields**: See additional fields below. Defaults to empty

Additional Fields
-----------------

Additional Fields can be added very easily. Let's take an example of adding the ip address of the client to every logged
message. To do this we add the ip address as a key/value to the [slf4j MDC](http://logback.qos.ch/manual/mdc.html)
(Mapped Diagnostic Context) so that the information persists for the length of the request, and then we inform
logback-gelf to look out for this mapping every time a message is logged.

1.  Store IP address in MDC

        // Somewhere in server code that wraps every request
        ...
        org.slf4j.MDC.put("ipAddress", getClientIpAddress());
        ...

2.  Inform logback-gelf of MDC mapping

        ...
        <appender name="GELF" class="me.moocar.logbackgelf.GelfAppender">
            ...
            <additionalField>ipAddress:_ip_address</additionalField>
            ...
        </appender>
        ...

The syntax for the additionalFields in logback.groovy is the following

    additionalFields = [<MDC Key>:<GELF Additional field name>, ...]

where `<MDC Key>` is unquoted and `<GELF Additional field name>` is quoted. It should also begin with an underscore (GELF standard)

Examples
--------

Check out the
[integration test](https://github.com/Moocar/logback-gelf/blob/master/src/test/java/me/moocar/logbackgelf/IntegrationTest.java). Just modify
[src/test/resources/logback.xml](https://github.com/Moocar/logback-gelf/blob/master/src/test/resources/logback.xml)
to point to your graylog2 server, and run the test. You should see the messages appearing in your graylog2
web interface.
