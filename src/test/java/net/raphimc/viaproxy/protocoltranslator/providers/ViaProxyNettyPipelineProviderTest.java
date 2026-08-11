/*
 * This file is part of ViaProxy - https://github.com/RaphiMC/ViaProxy
 * Copyright (C) 2021-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.raphimc.viaproxy.protocoltranslator.providers;

import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.protocol.ProtocolPipeline;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import net.raphimc.netminecraft.constants.ConnectionState;
import net.raphimc.netminecraft.constants.MCPipeline;
import net.raphimc.netminecraft.packet.registry.DefaultPacketRegistry;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViaProxyNettyPipelineProviderTest {

    @Test
    void requiresViaAndBothNetMinecraftRegistriesToOwnTheSameState() {
        final EmbeddedChannel c2p = new EmbeddedChannel();
        final EmbeddedChannel p2s = new EmbeddedChannel();
        final ProxyConnection connection = new ProxyConnection(new ChannelInitializer<>() {
            @Override
            protected void initChannel(final Channel channel) {
            }
        }, c2p);
        p2s.attr(ProxyConnection.PROXY_CONNECTION_ATTRIBUTE_KEY).set(connection);
        p2s.attr(MCPipeline.PACKET_REGISTRY_ATTRIBUTE_KEY)
                .set(new DefaultPacketRegistry(true, ProtocolVersion.v1_21_7.getVersion()));
        connection.setClientVersion(ProtocolVersion.v1_21_7);
        final MutableProtocolInfo protocolInfo = new MutableProtocolInfo();
        protocolInfo.clientState = State.CONFIGURATION;
        protocolInfo.serverState = State.CONFIGURATION;
        final UserConnectionImpl user = new UserConnectionImpl(p2s, true) {
            @Override
            public ProtocolInfo getProtocolInfo() {
                return protocolInfo;
            }
        };
        final ViaProxyNettyPipelineProvider provider = new ViaProxyNettyPipelineProvider();
        try {
            connection.setC2pConnectionState(ConnectionState.CONFIGURATION);
            connection.setP2sConnectionState(ConnectionState.LOGIN);
            assertFalse(provider.isJavaClientboundStateReady(user, State.CONFIGURATION));

            connection.setP2sConnectionState(ConnectionState.CONFIGURATION);
            p2s.attr(MCPipeline.PACKET_REGISTRY_ATTRIBUTE_KEY).get()
                    .setConnectionState(ConnectionState.CONFIGURATION);
            assertTrue(provider.isJavaClientboundStateReady(user, State.CONFIGURATION));

            protocolInfo.serverState = State.PLAY;
            assertFalse(provider.isJavaClientboundStateReady(user, State.PLAY));
        } finally {
            p2s.finishAndReleaseAll();
            c2p.finishAndReleaseAll();
        }
    }

    private static final class MutableProtocolInfo implements ProtocolInfo {
        private State clientState = State.HANDSHAKE;
        private State serverState = State.HANDSHAKE;
        private ProtocolVersion protocolVersion = ProtocolVersion.v1_21_7;
        private ProtocolVersion serverProtocolVersion = ProtocolVersion.v1_21_7;
        private String username;
        private UUID uuid;
        private ProtocolPipeline pipeline;
        private boolean compressionEnabled;

        @Override
        public State getClientState() {
            return this.clientState;
        }

        @Override
        public State getServerState() {
            return this.serverState;
        }

        @Override
        public void setClientState(final State clientState) {
            this.clientState = clientState;
        }

        @Override
        public void setServerState(final State serverState) {
            this.serverState = serverState;
        }

        @Override
        public ProtocolVersion protocolVersion() {
            return this.protocolVersion;
        }

        @Override
        public void setProtocolVersion(final ProtocolVersion protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        @Override
        public ProtocolVersion serverProtocolVersion() {
            return this.serverProtocolVersion;
        }

        @Override
        public void setServerProtocolVersion(final ProtocolVersion protocolVersion) {
            this.serverProtocolVersion = protocolVersion;
        }

        @Override
        public String getUsername() {
            return this.username;
        }

        @Override
        public void setUsername(final String username) {
            this.username = username;
        }

        @Override
        public UUID getUuid() {
            return this.uuid;
        }

        @Override
        public void setUuid(final UUID uuid) {
            this.uuid = uuid;
        }

        @Override
        public boolean compressionEnabled() {
            return this.compressionEnabled;
        }

        @Override
        public void setCompressionEnabled(final boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
        }

        @Override
        public ProtocolPipeline getPipeline() {
            return this.pipeline;
        }

        @Override
        public void setPipeline(final ProtocolPipeline pipeline) {
            this.pipeline = pipeline;
        }
    }
}
