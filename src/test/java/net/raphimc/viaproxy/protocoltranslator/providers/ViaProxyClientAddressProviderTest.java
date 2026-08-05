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
package net.raphimc.viaproxy.protocoltranslator.providers;

import com.viaversion.viaversion.connection.UserConnectionImpl;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViaProxyClientAddressProviderTest {

    @Test
    void usesClientToProxyAddressInsteadOfProxyToServerAddress() {
        final SocketAddress backendAddress = new InetSocketAddress("127.0.0.1", 19132);
        final SocketAddress clientAddress = new InetSocketAddress("14.19.55.74", 47768);
        final EmbeddedChannel p2s = new AddressedEmbeddedChannel(backendAddress);
        final EmbeddedChannel c2p = new AddressedEmbeddedChannel(clientAddress);
        final ProxyConnection proxyConnection = new ProxyConnection(new ChannelInitializer<>() {
            @Override
            protected void initChannel(final Channel channel) {
            }
        }, c2p);
        p2s.attr(ProxyConnection.PROXY_CONNECTION_ATTRIBUTE_KEY).set(proxyConnection);

        assertEquals(
                clientAddress,
                new ViaProxyClientAddressProvider().getClientAddress(new UserConnectionImpl(p2s))
        );
    }

    private static final class AddressedEmbeddedChannel extends EmbeddedChannel {

        private final SocketAddress remoteAddress;

        private AddressedEmbeddedChannel(final SocketAddress remoteAddress) {
            this.remoteAddress = remoteAddress;
        }

        @Override
        protected SocketAddress remoteAddress0() {
            return this.remoteAddress;
        }

    }

}
