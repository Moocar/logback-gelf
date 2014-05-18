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
    private static final String REGEX_IP_ADDRESS = "\\d+(\\.\\d+){3}";

    private InternetUtils() {
    }

    /**
     * Retrieves the local host's hostname. If found, the fully qualified domain name (FQDN) will be returned,
     * otherwise it will fallback to the unqualified domain name. E.g prefer guerrero.moocar.me over guerrero.
     */
    public static String getLocalHostName() throws SocketException, UnknownHostException {
        try {
            final String canonicalHostName = InetAddress.getLocalHost().getCanonicalHostName();
            if (isFQDN(canonicalHostName)) {
                return canonicalHostName;
            } else {
                return InetAddress.getLocalHost().getHostName();
            }
        } catch (UnknownHostException e) {
            NetworkInterface networkInterface = NetworkInterface.getNetworkInterfaces().nextElement();
            if (networkInterface == null) throw e;
            InetAddress ipAddress = networkInterface.getInetAddresses().nextElement();
            if (ipAddress == null) throw e;
            return ipAddress.getHostAddress();
        }
    }

    /**
     * Returns true is the hostname is a Fully Qualified Domain Name (FQDN)
     */
    private static boolean isFQDN(String hostname) {
        return hostname.contains(".") && !hostname.matches(REGEX_IP_ADDRESS);
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
