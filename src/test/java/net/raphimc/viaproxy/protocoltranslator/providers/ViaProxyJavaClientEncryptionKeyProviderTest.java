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

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ViaProxyJavaClientEncryptionKeyProviderTest {

    @Test
    void exposesJavaLoginSharedSecretWhenAvailable() {
        final EmbeddedChannel p2s = new EmbeddedChannel();
        final ProxyConnection proxyConnection = new ProxyConnection(new ChannelInitializer<>() {
            @Override
            protected void initChannel(final Channel channel) {
            }
        }, new EmbeddedChannel());
        p2s.attr(ProxyConnection.PROXY_CONNECTION_ATTRIBUTE_KEY).set(proxyConnection);
        final UserConnectionImpl user = new UserConnectionImpl(p2s);
        final ViaProxyJavaClientEncryptionKeyProvider provider = new ViaProxyJavaClientEncryptionKeyProvider();

        assertNull(provider.getJavaClientEncryptionKey(user));

        final SecretKey key = new SecretKeySpec(new byte[16], "AES");
        proxyConnection.setJavaClientEncryptionKey(key);
        assertSame(key, provider.getJavaClientEncryptionKey(user));
    }

}
