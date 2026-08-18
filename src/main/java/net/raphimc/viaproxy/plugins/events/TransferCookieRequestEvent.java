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

import com.google.common.net.HostAndPort;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.Channel;

public final class TransferCookieRequestEvent {

    private final ProtocolVersion clientVersion;
    private final HostAndPort clientHandshakeAddress;
    private final Channel clientChannel;
    private String cookieKey;

    public TransferCookieRequestEvent(final ProtocolVersion clientVersion, final HostAndPort clientHandshakeAddress, final Channel clientChannel) {
        this.clientVersion = clientVersion;
        this.clientHandshakeAddress = clientHandshakeAddress;
        this.clientChannel = clientChannel;
    }

    public ProtocolVersion getClientVersion() {
        return this.clientVersion;
    }

    public HostAndPort getClientHandshakeAddress() {
        return this.clientHandshakeAddress;
    }

    public Channel getClientChannel() {
        return this.clientChannel;
    }

    public String getCookieKey() {
        return this.cookieKey;
    }

    public void requestCookie(final String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Cookie key must not be blank");
        if (this.cookieKey != null && !this.cookieKey.equals(key)) {
            throw new IllegalStateException("A transfer cookie has already been requested");
        }
        this.cookieKey = key;
    }
}
