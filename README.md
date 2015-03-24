LOGBACK-GELF - A GELF Appender for Logback
==========================================

**NOTE: Version 0.2 is a BIG change. Check it out**

Use this appender to log messages with logback to a Graylog2 server
via GELF v1.1 messages.

If you don't know what [Graylog2](http://graylog2.org) is, jump on the
band wagon!

Installation
-----------------------------------

Simply add logback-gelf to your classpath. Either
[download the jar](https://github.com/Moocar/logback-gelf/downloads)
or if you're in [maven](http://mvnrepository.com/artifact/me.moocar/logback-gelf) land, the dependency details are below.

    <dependency>
        <groupId>me.moocar</groupId>
        <artifactId>logback-gelf</artifactId>
        <version>0.12</version>
    </dependency>

Features
--------

* Append via TCP or UDP (with chunking) to remote graylog server
* MDC k/v converted to fields
* Fields may have types
* Auto include logger_name
* Static fields
* Very Few dependencies

See defaults after the configuration section

Configuring Logback
---------------------

## As of version 0.2

Configuration has been reworked to be more idiomatic with logback. The
primary driver was adding TCP transport. Under 0.12 configuration, a
transport option would have been added to the main appender, but then
there would be no logical place to put TCP specific configuration such
as connectTimeout. UDP also has its own quirks, requiring chunking and
the option of GZIP.

So the new configuration provides both UDP and TCP appenders, but the
the Encoder/Layouts are (mostly) the same. This required a significant
refactor but will provide more flexibility going forward. For example,
adding a Kafka or AMPQ appender should be trivial.

An example of the new configuration is below:

    <configuration>
        <appender name="GELF UDP APPENDER" class="me.moocar.logbackgelf.GelfUDPAppender">
            <remoteHost>localhost</remoteHost>
            <port>12201</port>
            <encoder class="me.moocar.logbackgelf.GelfEncoder">
                <layout class="me.moocar.logbackgelf.GelfLayout">
                    <shortMessageLayout class="ch.qos.logback.classic.PatternLayout">
                        <pattern>%ex{short}%.100m</pattern>
                    </shortMessageLayout>
                    <fullMessageLayout class="ch.qos.logback.classic.PatternLayout">
                        <pattern>%rEx%m</pattern>
                    </fullMessageLayout>
                    <facility>logback-gelf-test</facility>
                    <useLoggerName>true</useLoggerName>
                    <useThreadName>true</useThreadName>
                    <useMarker>true</useMarker>
                    <host>Test</host>
                    <additionalField>ipAddress:_ip_address</additionalField>
                    <additionalField>requestId:_request_id</additionalField>
                    <includeFullMDC>true</includeFullMDC>
                    <fieldType>_request_id:long</fieldType>
                    <staticAdditionalField>_node_name:www013</staticAdditionalField>
                </layout>
            </encoder>
        </appender>

      <root level="debug">
        <appender-ref ref="GELF UDP APPENDER" />
      </root>
    </configuration>

To use TCP, simply replace the appender class with
`me.moocar.logbackgelf.SocketEncoderAppender`. In a perfect world, we
would use `ch.qos.logback.classic.net.SocketAppender`. Unfortunately,
it is hard coded to send serialized java objects over the wire,
whereas we obviously need GELF serialization. I may move this appender
into its own library in future.

The Appender Configuration is as follows:

### me.moocar.logbackgelf.SocketEncoderAppender

Send logs over TCP. Note that [gzip is not supported](https://github.com/Graylog2/graylog2-server/issues/127).

* **remoteHost**: The remote graylog server host to send log messages
  to (DNS or IP). Default: `"localhost"`
* **port**: The remote graylog server port. Default: `12201`
* **queueSize**: The number of log to keep in memory while the graylog
  server can't be reached. Default: `128`
* **acceptConnectionTimeout**: Milliseconds to wait for a connection
  to be established to the server before failing. Default: `1000` ms

### me.moocar.logbackgelf.GelfUDPAppender

Send logs over UDP. Messages will be chunked according to the [gelf spec](https://www.graylog.org/resources/gelf-2/)

* **remoteHost**: The remote graylog server host to send log messages
  to (DNS or IP). Default: `"localhost"`
* **port**: The remote graylog server port. Default: `12201`
* **queueSize**: The number of log to keep in memory before a flush is
  called (you probably won't need to change this). Default: `1024`

## me.moocar.logbackgelf.GelfLayout

This is where most configuration resides, since it's the bit that
actually converts a log event into a GELF compatible string.

* **facility**: The name of your service. Appears in facility column
  in graylog2-web-interface. Default: `"GELF"`
* **useLoggerName**: If true, an additional field call "_loggerName"
  will be added to each gelf message. Its contents will be the fully
  qualified name of the logger. e.g: com.company.Thingo. Default:
  `false`;
* **useThreadName**: If true, an additional field call "_threadName"
  will be added to each gelf message. Its contents will be the name of
  the thread. Default: `false`;
* **host** The hostname of the sending host. Displayed under `source`
  on web interface. Default: `getLocalHostName()`
* **useMarker**: If true, and the user has used an slf4j marker
  (http://slf4j.org/api/org/slf4j/Marker.html) in their log message by
  using one of the marker-overloaded log methods
  (http://slf4j.org/api/org/slf4j/Logger.html), then the
  marker.toString() will be added to the gelf message as the field
  "_marker". Default: `false`;
* **shortMessageLayout**: The
  [Layout](http://logback.qos.ch/manual/layouts.html) used to create
  the gelf `short_message` field. Shows up in the message column of
  the log summary in the web interface. Default: `"%ex{short}%.100m"`
  ([PatternLayout](http://logback.qos.ch/manual/layouts.html#ClassicPatternLayout))
* **fullMessageLayout**: The
  [Layout](http://logback.qos.ch/manual/layouts.html) used to create
  the gelf `full_message` field. Shows up in the message field of the
  log details in the web interface. Default: `""%rEx%m""`
  ([PatternLayout](http://logback.qos.ch/manual/layouts.html#ClassicPatternLayout))
* **additionalFields**: See additional fields below. Default: empty
* **fieldType**: See field type conversion below. Default: empty
  (fields sent as string)
* **staticAdditionalFields**: See static additional fields below.
  Default: empty
* **includeFullMDC**: See additional fields below. Default: `false`

Extra features
-----------------

## Additional Fields

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

If the property `includeFullMDC` is set to true, all fields from the MDC will be added to the gelf message. Any key, which is not
listed as `additionalField` will be prefixed with an underscore. Otherwise the field name will be obtained from the
corresponding `additionalField` mapping.

If the property `includeFullMDC` is set to false (default value) then only the keys listed as `additionalField` will be
added to a gelf message.

Static Additional Fields
-----------------

Use static additional fields when you want to add a static key value pair to every GELF message. Key is the additional
field key (and should thus begin with an underscore). The value is a static string.

E.g in the appender configuration:

        <appender name="GELF" class="me.moocar.logbackgelf.GelfAppender">
            ...
            <staticAdditionalField>_node_name:www013</staticAdditionalField>
            ...
        </appender>
        ...

Field type conversion
-----------------

You can configure a specific field to be converted to a numeric type. Key is the additional field key (and should thus
begin with an underscore), value is the type to convert to. Currently supported types are ``int``, ``long``, ``float``
and ``double``.

        <appender name="GELF" class="me.moocar.logbackgelf.GelfAppender">
            ...
            <fieldType>_request_id:long</fieldType>
            ...
        </appender>
        ...

logback-gelf will leave the field value alone (i.e.: send it as String) and print the stacktrace if the conversion fails.


Change Log
--------------------------------------

* Development version 0.13-SNAPSHOT (current Git `master`)
* Release [0.12] on 2014-Nov-04
  * Explicitly set Zipper string encoding to UTF-8 [#41](../../issues/41)
* Release [0.11] on 2014-May-18
  * Added field type conversion [#30](../../issues/30)
  * Use FQDN or fallback to hostname [#32](../../issues/32)
  * Update dependencies [#36](../../issues/36)
  * Remove copyright notice on InternetUtils [#38](../../issues/38)
  * Better testing of line and file in exceptions [#34](../../issues/34)
* Release [0.10p2] on 2014-Jan-12
  * Added hostName property [#28](../../issues/28)
  * Reverted Windows timeout [#29](../../issues/29)
