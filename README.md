LOGBACK-GELF - A GELF Appender for Logback
==========================================

A [Logback](http://logback.qos.ch/) appender that serializes logs to
[GELF](https://www.graylog.org/resources/gelf-2/) and transports them
to [Graylog](https://www.graylog.org/) servers.

**NOTE: Version 0.2 is NOT backwards compatible with previous versions
  (<= 0.12). [Read about the changes](#v02-changes)**

Depdency information
-----------------------------------

Logback-gelf is up on
[Maven Central](http://search.maven.org/#artifactdetails%7Cme.moocar%7Clogback-gelf%7C0.12%7Cjar).
If you're a maven user, the dependency information is below:

```xml
<dependency>
    <groupId>me.moocar</groupId>
    <artifactId>logback-gelf</artifactId>
    <version>0.12</version>
</dependency>
```

Features
--------

* Append via TCP or UDP (with chunking) to a remote graylog server
* MDC k/v converted to fields
* Fields may have types
* Auto include logger_name
* Static fields
* Very Few dependencies (Logback and GSON)

Configuring Logback
---------------------

__Note, 0.2 is a breaking version. It is NOT compatible with 0.12 and
previous versions. Read about the changes [here](#v02-changes)__

The minimal possible logback.xml you can write is something like.

```xml
<configuration>
    <appender name="GELF UDP APPENDER" class="me.moocar.logbackgelf.GelfUDPAppender">
        <encoder class="me.moocar.logbackgelf.GelfEncoder">
            <layout class="me.moocar.logbackgelf.GelfLayout"/>
        </encoder>
    </appender>
   <root level="debug">
    <appender-ref ref="GELF UDP APPENDER" />
  </root>
</configuration>
```

A more complete example that shows how you would overwrite many
default values:

```xml
<configuration>
    <appender name="GELF UDP APPENDER" class="me.moocar.logback.net.SocketEncoderAppender">
        <remoteHost>somehost.com</remoteHost>
        <port>12201</port>
        <encoder class="me.moocar.logbackgelf.GelfEncoder">
            <layout class="me.moocar.logbackgelf.GelfLayout">
                <!--An example of overwriting the short message pattern-->
                <shortMessageLayout class="ch.qos.logback.classic.PatternLayout">
                    <pattern>%ex{short}%.100m</pattern>
                </shortMessageLayout>
                <!-- Let's create HTML output of the full message. Because, why not-->
                <fullMessageLayout class="ch.qos.logback.classic.html.HTMLLayout">
                    <pattern>%relative%thread%mdc%level%logger%msg</pattern>
                </fullMessageLayout>
                <useLoggerName>true</useLoggerName>
                <useThreadName>true</useThreadName>
                <useMarker>true</useMarker>
                <host>Test</host>
                <additionalField>ipAddress:_ip_address</additionalField>
                <additionalField>requestId:_request_id</additionalField>
                <includeFullMDC>true</includeFullMDC>
                <fieldType>_request_id:long</fieldType>
                <!--Facility is not officially supporte in GELF anymore, but you can use staticAdditionalFields to do the same thing-->
                <staticAdditionalField>_facility:GELF</staticAdditionalField>
            </layout>
        </encoder>
    </appender>

    <root level="debug">
        <appender-ref ref="GELF UDP APPENDER" />
    </root>
</configuration>
```
## GelfLayout

`me.moocar.logbackgelf.GelfLayout`

This is where most configuration resides, since it's the part that
actually converts a log event into a GELF compatible JSON string.

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
  Note, now that facility is deprecated, use this to set a facility
  Default: empty
* **includeFullMDC**: See additional fields below. Default: `false`

## Transports

Both UDP and TCP transports are supported. UDP is the recommended
graylog transport.

### UDP

UDP can be configured using the
`me.moocar.logbackgelf.GelfUDPAppender` appender. Once messages reach
a certain size, they will bechunked according to the
[gelf spec](https://www.graylog.org/resources/gelf-2/). This allows
for a theoretical maximum encoded log size of about 1 megabyte
(1040384 bytes).

* **remoteHost**: The remote graylog server host to send log messages
  to (DNS or IP). Default: `"localhost"`
* **port**: The remote graylog server port. Default: `12201`
* **queueSize**: The number of log to keep in memory before a flush is
  called (you probably won't need to change this). Default: `1024`

### TCP

TCP transport can be configured using the
`me.moocar.logback.net.SocketEncoderAppender` appender. Unfortunately,
the built in
[Socket Appender](http://logback.qos.ch/manual/appenders.html#SocketAppender)
doesn't give you control of how logs are encoded before being sent
over TCP, which is why you have to use this appender. To make the
system as flexible as possible, I moved this new appender into its
[own library](), so if you want to use it, you'll need to add it to
your depenencies too. Also note that due to an unresolved
[Graylog issue](https://github.com/Graylog2/graylog2-server/issues/127),
GZIP is not supported when using TCP.

```xml
<dependency>
    <groupId>me.moocar</groupId>
    <artifactId>socket-encoder-appender</artifactId>
    <version>0.12</version>
</dependency>
```

Then, replace the top level Gelf appender with
`me.moocar.logback.net.SocketEncoderAppender`.

```xml
<appender name="GELF TCP APPENDER" class="me.moocar.logback.net.SocketEncoderAppender">
    <encoder class="me.moocar.logbackgelf.GelfEncoder">
        <layout class="me.moocar.logbackgelf.GelfLayout">
            ....
        </layout>
    </encoder>
</appender>
```

* **remoteHost**: The remote graylog server host to send log messages
  to (DNS or IP). Default: `"localhost"`
* **port**: The remote graylog server port. Default: `12201`
* **queueSize**: The number of log to keep in memory while the graylog
  server can't be reached. Default: `128`
* **acceptConnectionTimeout**: Milliseconds to wait for a connection
  to be established to the server before failing. Default: `1000`

Extra features
-----------------

## Additional Fields

Additional Fields are extra k/v pairs that can be added to the GELF
json, and thus searched as structured data using graylog. In the slf4j
world, [MDC](http://logback.qos.ch/manual/mdc.html) (Mapped Diagnostic
Context) is an excellent way of programattically adding fields to your
GELF messages.

Let's take an example of adding the ip address of the client to every
logged message. To do this we add the ip address as a key/value to the
MDC so that the information persists for the length of the request,
and then we inform logback-gelf to look out for this mapping every
time a message is logged.

1.  Store IP address in MDC

```java
// Somewhere in server code that wraps every request
...
org.slf4j.MDC.put("ipAddress", getClientIpAddress());
...
```

2.  Inform logback-gelf of MDC mapping

```xml
...
<appender name="GELF" class="me.moocar.logbackgelf.GelfAppender">
    ...
    <additionalField>ipAddress:_ip_address</additionalField>
    ...
</appender>
...
```

If the property `includeFullMDC` is set to true, all fields from the
MDC will be added to the gelf message. Any key, which is not listed as
`additionalField` will be prefixed with an underscore. Otherwise the
field name will be obtained from the corresponding `additionalField`
mapping.

If the property `includeFullMDC` is set to false (default value) then
only the keys listed as `additionalField` will be added to a gelf
message.

### Static Additional Fields

Use static additional fields when you want to add a static key value
pair to every GELF message. Key is the additional field key (and
should thus begin with an underscore). The value is a static string.

Now that the GELF `facility` is deprecated, this is how you add a
static facility.

E.g in the appender configuration:

```xml
<appender class="me.moocar.logbackgelf.GelfLayout">
    ...
    <staticAdditionalField>_node_name:www013</staticAdditionalField>
    <staticAdditionalField>_facility:GELF</staticAdditionalField>
    ...
</appender>
```

### Field type conversion

You can configure a specific field to be converted to a numeric type.
Key is the additional field key (and should thus begin with an
underscore), value is the type to convert to. Currently supported
types are ``int``, ``long``, ``float`` and ``double``.

```xml
<appender class="me.moocar.logbackgelf.GelfLayout">
    ...
    <fieldType>_request_id:long</fieldType>
    ...
</appender>
```

If the conversion fails, logback-gelf will leave the field value alone
(i.e.: send it as String) and print the stacktrace

V0.2 Changes
------------

Configuration has been reworked to fit better into the logback
ecosystem. The primary driver was adding TCP transport. Under 0.12
configuration, a transport option would have been added to the main
appender, but then there would be no logical place to put TCP specific
configuration such as connectTimeout. UDP also has its own quirks,
requiring chunking and the option of GZIP.

So the new configuration follows the logback way and provides both UDP
and TCP appenders, and the GELF serialization logic is now in a
GelfLayout. This required a significant refactor but will provide more
flexibility going forward. For example, adding a Kafka or AMPQ
appender should now be trivial.

To use TCP, simply replace the
appender class with `me.moocar.logbackgelf.SocketEncoderAppender`. In
a perfect world, we would use
`ch.qos.logback.classic.net.SocketAppender`. Unfortunately, it is hard
coded to send serialized java objects over the wire, whereas we
obviously need GELF serialization. I may move this appender into its
own library in future.

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
