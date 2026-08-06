/*
 * This file is part of ViaProxy - https://github.com/RaphiMC/ViaProxy
 * Copyright (C) 2021-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.raphimc.viaproxy.proxy.packethandler;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import net.raphimc.netminecraft.constants.ConnectionState;
import net.raphimc.netminecraft.packet.impl.login.S2CLoginGameProfilePacket;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolBoundaryDiagnosticsTest {

    @Test
    void duplicateProfileDiagnosticPreservesTheProtocolEvidenceWithoutIdentityData() {
        final EmbeddedChannel c2p = new EmbeddedChannel();
        final ProxyConnection connection = new ProxyConnection(new ChannelInitializer<>() {
            @Override
            protected void initChannel(final Channel channel) {
            }
        }, c2p);
        final UUID fixtureUuid = UUID.fromString("52000000-1234-5678-9abc-def012345678");
        final String fixtureName = "sensitive-fixture-name";
        try {
            connection.setClientVersion(ProtocolVersion.v1_21_7);
            connection.setC2pConnectionState(ConnectionState.LOGIN);
            connection.setP2sConnectionState(ConnectionState.LOGIN);

            final String diagnostic = ProtocolBoundaryDiagnostics.describeDuplicateLoginProfile(
                    "safe-correlation-id", connection,
                    new S2CLoginGameProfilePacket(fixtureUuid, fixtureName, List.of()), 2);

            assertTrue(diagnostic.contains("event=duplicate_login_finished"));
            assertTrue(diagnostic.contains("correlationId=safe-correlation-id"));
            assertTrue(diagnostic.contains("packetId=0x2"));
            assertTrue(diagnostic.contains("safePrefix=0x2 52 [remaining profile bytes redacted]"));
            assertTrue(diagnostic.contains("c2pState=LOGIN"));
            assertFalse(diagnostic.contains(fixtureUuid.toString()));
            assertFalse(diagnostic.contains(fixtureName));
        } finally {
            c2p.finishAndReleaseAll();
        }
    }

}
