/*
 * This file is part of ViaProxy - https://github.com/RaphiMC/ViaProxy
 * Copyright (C) 2021-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viaproxy.util;

import java.net.InetSocketAddress;

public final class LoginRoutingUtil {

    public static final String JETEST_LOGIN_HOST = "jetest-login.easecation.net";
    public static final String JEPROD_LOGIN_HOST = "jeprod-login.easecation.net";
    public static final String JETEST_ENTRY_HOST = "jetest.easecation.net";
    public static final String JEPROD_ENTRY_HOST = "jeprod.easecation.net";

    private static final String JETEST_LOGIN_ENTRY_HOST = "bbdev.easecation.net";
    private static final int JETEST_LOGIN_ENTRY_PORT = 19133;
    private static final String JEPROD_LOGIN_ENTRY_HOST = "play.easecation.net";
    private static final int JEPROD_LOGIN_ENTRY_PORT = 19132;

    private LoginRoutingUtil() {
    }

    /**
     * Returns the login reconnect hostname for the given transfer target, or null if the target is not an EaseCation login entry.
     */
    public static String getLoginReconnectHost(final InetSocketAddress transferTarget) {
        if (transferTarget == null || transferTarget.getHostString() == null) {
            return null;
        }
        if (transferTarget.getHostString().equalsIgnoreCase(JETEST_LOGIN_ENTRY_HOST) && transferTarget.getPort() == JETEST_LOGIN_ENTRY_PORT) {
            return JETEST_LOGIN_HOST;
        }
        if (transferTarget.getHostString().equalsIgnoreCase(JEPROD_LOGIN_ENTRY_HOST) && transferTarget.getPort() == JEPROD_LOGIN_ENTRY_PORT) {
            return JEPROD_LOGIN_HOST;
        }
        return null;
    }

    /**
     * Returns whether the given handshake host is the EaseCation test login reconnect hostname.
     */
    public static boolean isJetestLoginHost(final String handshakeHost) {
        return handshakeHost != null && handshakeHost.equalsIgnoreCase(JETEST_LOGIN_HOST);
    }

    /**
     * Returns whether the given handshake host is the EaseCation production login reconnect hostname.
     */
    public static boolean isJeprodLoginHost(final String handshakeHost) {
        return handshakeHost != null && handshakeHost.equalsIgnoreCase(JEPROD_LOGIN_HOST);
    }

    /**
     * Returns the configured login backend address for the given handshake host, or null if the host is not an EaseCation login reconnect hostname.
     */
    public static String getLoginBackendAddress(final String handshakeHost, final String jetestAddress, final String jeprodAddress) {
        if (isJetestLoginHost(handshakeHost)) {
            return jetestAddress;
        }
        if (isJeprodLoginHost(handshakeHost)) {
            return jeprodAddress;
        }
        return null;
    }

    /**
     * Returns the ordinary JE entry hostname for the given login reconnect hostname, or null if the host is not a login reconnect hostname.
     */
    public static String getOrdinaryEntryHost(final String handshakeHost) {
        if (isJetestLoginHost(handshakeHost)) {
            return JETEST_ENTRY_HOST;
        }
        if (isJeprodLoginHost(handshakeHost)) {
            return JEPROD_ENTRY_HOST;
        }
        return null;
    }

}
