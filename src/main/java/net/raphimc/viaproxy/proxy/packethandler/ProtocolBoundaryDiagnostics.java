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

import com.viaversion.viaversion.api.connection.ProtocolInfo;
import io.netty.buffer.ByteBuf;
import net.raphimc.netminecraft.constants.MCPipeline;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.netminecraft.packet.impl.login.S2CLoginGameProfilePacket;
import net.raphimc.netminecraft.packet.registry.PacketRegistry;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;

final class ProtocolBoundaryDiagnostics {

    private ProtocolBoundaryDiagnostics() {
    }

    static String describeDuplicateLoginProfile(final String correlationId,
                                                final ProxyConnection connection,
                                                final S2CLoginGameProfilePacket packet,
                                                final int occurrence) {
        final ProtocolInfo viaState = connection.getUserConnection() != null
                ? connection.getUserConnection().getProtocolInfo() : null;
        final PacketRegistry registry = connection.getC2P()
                .attr(MCPipeline.PACKET_REGISTRY_ATTRIBUTE_KEY).get();
        final String stateSummary = "c2pState=" + connection.getC2pConnectionState()
                + " p2sState=" + connection.getP2sConnectionState()
                + " registryState=" + (registry != null ? registry.getConnectionState() : "missing")
                + " viaClientState=" + (viaState != null ? viaState.getClientState() : "missing")
                + " viaServerState=" + (viaState != null ? viaState.getServerState() : "missing");

        if (registry == null || connection.getClientVersion() == null) {
            return "event=duplicate_login_finished correlationId=" + correlationId
                    + " direction=P2S_to_C2P handler=LoginPacketHandler packetClass="
                    + packet.getClass().getSimpleName() + " occurrence=" + occurrence + " " + stateSummary
                    + " encoding=unavailable";
        }

        final ByteBuf logicalPacket = connection.getC2P().alloc().buffer();
        try {
            final int packetId = registry.getPacketId(packet);
            PacketTypes.writeVarInt(logicalPacket, packetId);
            final int bodyIndex = logicalPacket.writerIndex();
            packet.write(logicalPacket, connection.getClientVersion().getVersion());
            final String bodyPrefix = logicalPacket.writerIndex() > bodyIndex
                    ? String.format("%02x [remaining profile bytes redacted]", logicalPacket.getUnsignedByte(bodyIndex))
                    : "empty";
            return "event=duplicate_login_finished correlationId=" + correlationId
                    + " direction=P2S_to_C2P handler=LoginPacketHandler packetClass="
                    + packet.getClass().getSimpleName() + " packetId=0x" + Integer.toHexString(packetId)
                    + " occurrence=" + occurrence + " protocolVersion=" + connection.getClientVersion().getVersion()
                    + " logicalLength=" + logicalPacket.readableBytes()
                    + " readerIndex=" + logicalPacket.readerIndex()
                    + " writerIndex=" + logicalPacket.writerIndex()
                    + " safePrefix=0x" + Integer.toHexString(packetId) + " " + bodyPrefix
                    + " " + stateSummary;
        } catch (Throwable error) {
            return "event=duplicate_login_finished correlationId=" + correlationId
                    + " direction=P2S_to_C2P handler=LoginPacketHandler packetClass="
                    + packet.getClass().getSimpleName() + " occurrence=" + occurrence + " " + stateSummary
                    + " encodingError=" + error.getClass().getSimpleName();
        } finally {
            logicalPacket.release();
        }
    }

}
