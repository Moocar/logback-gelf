package me.moocar.logbackgelf.util;

/*
 *  Copyright (c) 2013 OCLC, Inc. All Rights Reserved.
 *
 *  OCLC proprietary information: the enclosed materials contain proprietary information of OCLC Online Computer
 *  Library Center, Inc. and shall not be disclosed in whole or in any part to any third party or used by any person
 *  for any purpose, without written consent of OCLC, Inc.  Duplication of any portion of these materials shall include
 *  this notice.
 */

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

public class InternetUtils {
    private InternetUtils() {
    }

    /**
     * Retrieves the localhost's hostname, or if unavailable, the ip address
     */
    public static String getLocalHostName() throws SocketException, UnknownHostException {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            NetworkInterface networkInterface = NetworkInterface.getNetworkInterfaces().nextElement();
            if (networkInterface == null) throw e;
            InetAddress ipAddress = networkInterface.getInetAddresses().nextElement();
            if (ipAddress == null) throw e;
            return ipAddress.getHostAddress();
        }
    }

    /**
     * Gets the Inet address for the graylog2ServerHost and gives a specialised error message if an exception is thrown
     *
     * @return The Inet address for graylog2ServerHost
     */
    public static InetAddress getInetAddress(String hostName) {
        try {
            return InetAddress.getByName(hostName);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Unknown host: " + e.getMessage() +
                    ". Make sure you have specified the 'graylog2ServerHost' property correctly in your logback.xml'");
        }
    }
}
