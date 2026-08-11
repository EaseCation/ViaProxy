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

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import net.lenni0451.mcstructs.text.TextComponent;
import net.raphimc.netminecraft.constants.ConnectionState;
import net.raphimc.netminecraft.constants.MCPipeline;
import net.raphimc.netminecraft.netty.codec.PacketCodec;
import net.raphimc.netminecraft.netty.codec.PacketSizer;
import net.raphimc.netminecraft.packet.Packet;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.netminecraft.packet.impl.configuration.S2CConfigCookieRequestPacket;
import net.raphimc.netminecraft.packet.impl.configuration.S2CConfigKeepAlivePacket;
import net.raphimc.netminecraft.packet.impl.login.S2CLoginDisconnectPacket;
import net.raphimc.netminecraft.packet.registry.DefaultPacketRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Java1218KeepAliveStateBoundaryTest {

    private static final int JAVA_1_21_8_PROTOCOL = 772;
    private static final int CONFIGURATION_KEEP_ALIVE_ID = 0x04;

    @Test
    void configurationKeepAliveIsExactlyOnePacketIdAndOneLong() {
        final long keepAliveId = 0x0102030405060708L;
        final ByteBuf frame = encodeFrame(
                new S2CConfigKeepAlivePacket(keepAliveId), ConnectionState.CONFIGURATION);
        try {
            final ByteBuf view = frame.duplicate();
            assertEquals(9, PacketTypes.readVarInt(view));
            assertEquals(CONFIGURATION_KEEP_ALIVE_ID, PacketTypes.readVarInt(view));
            assertEquals(keepAliveId, view.readLong());
            assertEquals(0, view.readableBytes());

            final S2CConfigKeepAlivePacket decoded = assertInstanceOf(
                    S2CConfigKeepAlivePacket.class,
                    decodeFrame(frame, ConnectionState.CONFIGURATION));
            assertEquals(keepAliveId, decoded.id);
        } finally {
            frame.release();
        }
    }

    @Test
    void reproducesTheProductionKeepAliveToLoginDecoderFailure() {
        // LOGIN packet 0x04 is custom_query. Its decoder consumes one zero byte as a VarInt query
        // id and then treats the next long byte (0x08) as a string length although only six remain.
        final ByteBuf frame = encodeFrame(
                new S2CConfigKeepAlivePacket(0x0008000000000000L),
                ConnectionState.CONFIGURATION);
        try {
            assertThrows(RuntimeException.class,
                    () -> decodeFrame(frame, ConnectionState.LOGIN));
        } finally {
            frame.release();
        }
    }

    @Test
    void loginDisconnectIdBecomesConfigurationCookieRequestAfterTheFailure() {
        final String reason = "An unhandled error occurred: IndexOutOfBoundsException";
        final ByteBuf frame = encodeFrame(
                new S2CLoginDisconnectPacket(TextComponent.of(reason)), ConnectionState.LOGIN);
        try {
            final ByteBuf view = frame.duplicate();
            PacketTypes.readVarInt(view);
            assertEquals(0x00, PacketTypes.readVarInt(view));

            final S2CConfigCookieRequestPacket decoded = assertInstanceOf(
                    S2CConfigCookieRequestPacket.class,
                    decodeFrame(frame, ConnectionState.CONFIGURATION));
            assertTrue(decoded.key.contains(reason));
            assertTrue(decoded.key.indexOf(' ') >= 0 || decoded.key.indexOf('{') >= 0,
                    "The disconnect reason is not a valid ResourceLocation cookie key");
        } finally {
            frame.release();
        }
    }

    private static ByteBuf encodeFrame(final Packet packet, final ConnectionState state) {
        final DefaultPacketRegistry registry = new DefaultPacketRegistry(false, JAVA_1_21_8_PROTOCOL);
        registry.setConnectionState(state);
        final EmbeddedChannel channel = codecChannel(registry);
        try {
            assertTrue(channel.writeOutbound(packet));
            return channel.readOutbound();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static Packet decodeFrame(final ByteBuf frame, final ConnectionState state) {
        final DefaultPacketRegistry registry = new DefaultPacketRegistry(true, JAVA_1_21_8_PROTOCOL);
        registry.setConnectionState(state);
        final EmbeddedChannel channel = codecChannel(registry);
        try {
            assertTrue(channel.writeInbound(frame.copy()));
            return channel.readInbound();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static EmbeddedChannel codecChannel(final DefaultPacketRegistry registry) {
        final EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(MCPipeline.PACKET_REGISTRY_ATTRIBUTE_KEY).set(registry);
        channel.pipeline().addLast(MCPipeline.SIZER_HANDLER_NAME, new PacketSizer());
        channel.pipeline().addLast(MCPipeline.PACKET_CODEC_HANDLER_NAME, new PacketCodec());
        return channel;
    }
}
