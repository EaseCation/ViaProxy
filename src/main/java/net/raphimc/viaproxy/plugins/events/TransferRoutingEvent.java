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
package net.raphimc.viaproxy.plugins.events;

import net.raphimc.viaproxy.proxy.packethandler.TransferRoutingMode;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;

import java.net.InetSocketAddress;
import java.util.Objects;

public final class TransferRoutingEvent {

    private static final int MAX_COOKIE_PAYLOAD_SIZE = 5_120;

    private final ProxyConnection proxyConnection;
    private final InetSocketAddress originalTarget;
    private TransferRoutingMode mode = TransferRoutingMode.RECONNECT_THROUGH_VIAPROXY;
    private String reconnectCookieKey;
    private byte[] reconnectCookiePayload;

    public TransferRoutingEvent(final ProxyConnection proxyConnection, final InetSocketAddress originalTarget) {
        this.proxyConnection = proxyConnection;
        this.originalTarget = Objects.requireNonNull(originalTarget, "originalTarget");
    }

    public ProxyConnection getProxyConnection() {
        return this.proxyConnection;
    }

    public InetSocketAddress getOriginalTarget() {
        return this.originalTarget;
    }

    public TransferRoutingMode getMode() {
        return this.mode;
    }

    public void setMode(final TransferRoutingMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public String getReconnectCookieKey() {
        return this.reconnectCookieKey;
    }

    public byte[] getReconnectCookiePayload() {
        return this.reconnectCookiePayload != null ? this.reconnectCookiePayload.clone() : null;
    }

    public void setReconnectCookie(final String key, final byte[] payload) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Cookie key must not be blank");
        if (payload == null) throw new IllegalArgumentException("Cookie payload must not be null");
        if (payload.length > MAX_COOKIE_PAYLOAD_SIZE) throw new IllegalArgumentException("Cookie payload is too large");
        this.reconnectCookieKey = key;
        this.reconnectCookiePayload = payload.clone();
    }
}
