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
package net.raphimc.viaproxy.protocoltranslator.impl;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.platform.ViaCodecHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.viabedrock.netty.PacketCodec;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.proxy.util.ProtocolFramingDiagnostics;
import net.raphimc.viaproxy.util.logging.Logger;

public class ViaProxyViaCodec extends ViaCodecHandler {

    public ViaProxyViaCodec(UserConnection user) {
        super(user);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        final boolean bedrockClientbound = msg instanceof ByteBuf
                && ctx.pipeline().get(PacketCodec.NAME) != null;
        if (bedrockClientbound) {
            final ByteBuf payload = (ByteBuf) msg;
            final int packetId = readPacketId(payload);
            final ClientboundBedrockPackets packetType = ClientboundBedrockPackets.getPacket(packetId);
            ProtocolFramingDiagnostics.beginNetworkPacket(ctx.channel(),
                    packetType != null ? packetType.toString() : "unknown",
                    this.connection.getProtocolInfo().getServerState().toString(),
                    packetId, payload);
        }
        try {
            if (ViaProxy.getConfig().shouldIgnoreProtocolTranslationErrors()) {
                try {
                    super.channelRead(ctx, msg);
                } catch (Throwable e) {
                    Logger.LOGGER.error("ProtocolTranslator packet translation error occurred", e);
                }
            } else {
                super.channelRead(ctx, msg);
            }
        } finally {
            if (bedrockClientbound) {
                ProtocolFramingDiagnostics.endNetworkPacket(ctx.channel());
            }
        }
    }

    private static int readPacketId(final ByteBuf payload) {
        try {
            return PacketTypes.readVarInt(payload.duplicate());
        } catch (Throwable ignored) {
            return -1;
        }
    }

}
