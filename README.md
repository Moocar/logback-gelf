LOGBACK-GELF - A GELF Appender for Logback
==========================================

Use this appender to log messages with logback to a Graylog2 server via GELF messages. Supports Additional Fields and chunking.

If you don't know what Graylog2 is, jump on the band wagon! [Graylog2](http://graylog2.org)

To use with a maven project
---------------------------

1. Clone git repo

        user:~$ git clone git://github.com/Moocar/logback-gelf.git

2.  install to local maven repo

        user:~$ cd logback-gelf
        user:~$ mvn install

3.  Add as dependency to your project's pom.xml

        <dependencies>
            ...
            <dependency>
                <groupId>logback-gelf</groupId>
                <artifactId>logback-gelf</artifactId>
                <version>0.2</version>
            </dependency>
            ...
        </dependencies>

Configuring Logback
---------------------

The following assumes you are using groovy for your logback configuration.

    /* src/main/resources/logback.groovy */

    import org.logbackgelf.GelfAppender
    import static ch.qos.logback.classic.Level.DEBUG

    appender("GELF", GelfAppender) {
        facility = "logback-gelf-test"
        graylog2ServerHost = "localhost"
        graylog2ServerPort = 12201
        useLoggerName = true
        graylog2ServerVersion = "0.9.6"
        chunkThreshold = 1000
        additionalFields = [ipAddress:"_ip_address"]
    }

    root(DEBUG, ["GELF"])

Properties
----------

*   **facility**: The name of your service. Appears in facility column in graylog2-web-interface. Defaults to "GELF"
*   **graylog2ServerHost**: The hostname of the graylog2 server to send messages to. Defaults to "localhost"
*   **graylog2ServerPort**: The graylog2ServerPort of the graylog2 server to send messages to. Defaults to 12201
*   **useLoggerName**: If true, an additional field call "_loggerName" will be added to each gelf message. Its contents
will be the fully qualified name of the logger. e.g: com.company.Thingo. Defaults to false;
*   **graylog2ServerVersion**: Specify which version the graylog2-server is. This is important because the GELF headers
changed from 0.9.5 -> 0.9.6. Allowed values = 0.9.5 and 0.9.6. Defaults to "0.9.5"
*   **chunkThreshold**: The maximum number of bytes allowed by the payload before the message should be chunked into
smaller packets. Defaults to 1000
*   **additionalFields**: See additional fields below. Defaults to empty

Additional Fields
-----------------

Additional Fields can be added very easily. Let's take an example of adding the ip address of the client to every logged
message. To do this we add the ip address as a key/value to the slf4j MDC (Mapped Diagnostic Context) so that the
information persists for the length of the request, and then we inform logback-gelf to look out for this mapping every
time a message is logged.

1.  Store IP address in MDC

        // Somewhere in server code that wraps every request
        ...
        org.slf4j.MDC.put("ipAddress", getClientIpAddress());
        ...

2.  Inform logback-gelf of MDC mapping

        /* src/main/resources/logback.groovy */
        ...
        appender("GELF", GelfAppender) {
            ...
            additionalFields = [ipAddress:"_ip_address"]
            ...
        }

The syntax for the additionalFields in logback.groovy is the following

    additionalFields = [<MDC Key>:<GELF Additional field name>, ...]

where `<MDC Key>` is unquoted and `<GELF Additional field name>` is quoted. It should also begin with an underscore

Examples
--------

Check out src/test/java/logbackgelf/IntegrationTest.java. Just modify the src/test/resources/logback.groovy to point to
your graylog2 server, and run the test. You should see the messages appearing in your graylog2 web interface.