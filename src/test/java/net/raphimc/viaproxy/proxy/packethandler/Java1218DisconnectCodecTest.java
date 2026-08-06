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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.lenni0451.mcstructs.text.TextComponent;
import net.raphimc.netminecraft.constants.ConnectionState;
import net.raphimc.netminecraft.constants.MCPipeline;
import net.raphimc.netminecraft.netty.codec.PacketCodec;
import net.raphimc.netminecraft.netty.codec.PacketSizer;
import net.raphimc.netminecraft.packet.Packet;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.netminecraft.packet.impl.configuration.S2CConfigDisconnectPacket;
import net.raphimc.netminecraft.packet.impl.configuration.S2CConfigFinishConfigurationPacket;
import net.raphimc.netminecraft.packet.impl.login.S2CLoginGameProfilePacket;
import net.raphimc.netminecraft.packet.impl.play.S2CPlayDisconnectPacket;
import net.raphimc.netminecraft.packet.registry.DefaultPacketRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Java1218DisconnectCodecTest {

    private static final int JAVA_1_21_8_PROTOCOL = 772;
    private static final int CONFIGURATION_DISCONNECT_ID = 0x02;
    private static final int[] OBSERVED_INVALID_TAG_IDS = {82, 48, 42, -48, -11, -30, 43, -47};

    @Test
    void configurationDisconnectRoundTripsThroughTheFinalJavaFrameCodec() {
        final TextComponent styledAndNested = TextComponent.of("root")
                .styled(style -> style.setBold(true).setColor(0x12AB34))
                .append(TextComponent.translation("disconnect.genericReason", TextComponent.of("nested")));
        final List<TextComponent> reasons = List.of(
                TextComponent.of("Plain text"),
                TextComponent.of("中文和 Unicode 🙂"),
                TextComponent.translation("multiplayer.disconnect.server_shutdown"),
                styledAndNested,
                TextComponent.empty(),
                TextComponent.of("较长原因🙂".repeat(1000)),
                TextComponent.of("§c来自 Bedrock 的断开原因 🙂")
        );

        for (TextComponent reason : reasons) {
            final ByteBuf frame = encodeFrame(new S2CConfigDisconnectPacket(reason), ConnectionState.CONFIGURATION);
            try {
                final FrameInspection inspection = inspectFrame(frame);
                assertEquals(CONFIGURATION_DISCONNECT_ID, inspection.packetId());
                assertEquals(inspection.frameLength(), inspection.logicalLength());
                assertTrue(inspection.firstBodyByte() >= 0 && inspection.firstBodyByte() <= 12);

                final S2CConfigDisconnectPacket decoded = assertInstanceOf(
                        S2CConfigDisconnectPacket.class, decodeFrame(frame, ConnectionState.CONFIGURATION));
                assertEquals(reason.asUnformattedString(), decoded.reason.asUnformattedString());
                final ByteBuf reencoded = encodeFrame(decoded, ConnectionState.CONFIGURATION);
                try {
                    assertTrue(ByteBufUtil.equals(frame, reencoded));
                } finally {
                    reencoded.release();
                }
            } finally {
                frame.release();
            }
        }
    }

    @Test
    void configurationAndPlayDisconnectsUseTheirStateSpecificRegistries() {
        final TextComponent reason = TextComponent.of("state-specific disconnect");
        final ByteBuf finishConfiguration = encodeFrame(
                new S2CConfigFinishConfigurationPacket(), ConnectionState.CONFIGURATION);
        final ByteBuf playDisconnect = encodeFrame(new S2CPlayDisconnectPacket(reason), ConnectionState.PLAY);
        try {
            assertInstanceOf(S2CConfigFinishConfigurationPacket.class,
                    decodeFrame(finishConfiguration, ConnectionState.CONFIGURATION));
            final S2CPlayDisconnectPacket decoded = assertInstanceOf(
                    S2CPlayDisconnectPacket.class, decodeFrame(playDisconnect, ConnectionState.PLAY));
            assertEquals(reason, decoded.reason);
        } finally {
            finishConfiguration.release();
            playDisconnect.release();
        }
    }

    @Test
    void observedTagIdsAreTheFirstUuidByteOfALateLoginProfile() {
        for (int signedTagId : OBSERVED_INVALID_TAG_IDS) {
            final int unsignedTagId = signedTagId & 0xFF;
            final UUID fixtureUuid = new UUID((long) unsignedTagId << 56, 0L);
            final ByteBuf loginFrame = encodeFrame(
                    new S2CLoginGameProfilePacket(fixtureUuid, "fixture-player", List.of()), ConnectionState.LOGIN);
            try {
                final FrameInspection inspection = inspectFrame(loginFrame);
                assertEquals(CONFIGURATION_DISCONNECT_ID, inspection.packetId());
                assertEquals(unsignedTagId, inspection.firstBodyByte());

                assertThrows(RuntimeException.class,
                        () -> decodeFrame(loginFrame, ConnectionState.CONFIGURATION));
            } finally {
                loginFrame.release();
            }
        }
    }

    @Test
    void invalidAndTruncatedConfigurationNbtFailWithinTheirOwnFrame() {
        for (int signedTagId : OBSERVED_INVALID_TAG_IDS) {
            final ByteBuf frame = frameLogicalPacket(CONFIGURATION_DISCONNECT_ID, (byte) signedTagId);
            try {
                assertThrows(RuntimeException.class,
                        () -> decodeFrame(frame, ConnectionState.CONFIGURATION));
            } finally {
                frame.release();
            }
        }

        // String tag id followed by only one of the two required UTF length bytes.
        final ByteBuf truncated = frameLogicalPacket(CONFIGURATION_DISCONNECT_ID, (byte) 8, (byte) 0);
        try {
            assertThrows(RuntimeException.class,
                    () -> decodeFrame(truncated, ConnectionState.CONFIGURATION));
        } finally {
            truncated.release();
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

    private static FrameInspection inspectFrame(final ByteBuf frame) {
        final ByteBuf view = frame.duplicate();
        final int frameLength = PacketTypes.readVarInt(view);
        assertEquals(frameLength, view.readableBytes());
        final int logicalStart = view.readerIndex();
        final int packetId = PacketTypes.readVarInt(view);
        final int firstBodyByte = view.getUnsignedByte(view.readerIndex());
        view.skipBytes(view.readableBytes());
        assertEquals(view.writerIndex(), view.readerIndex());
        return new FrameInspection(frameLength, view.writerIndex() - logicalStart, packetId, firstBodyByte);
    }

    private static ByteBuf frameLogicalPacket(final int packetId, final byte... body) {
        final ByteBuf logical = Unpooled.buffer();
        final ByteBuf frame = Unpooled.buffer();
        try {
            PacketTypes.writeVarInt(logical, packetId);
            logical.writeBytes(body);
            PacketTypes.writeVarInt(frame, logical.readableBytes());
            frame.writeBytes(logical, logical.readerIndex(), logical.readableBytes());
            return frame;
        } finally {
            logical.release();
        }
    }

    private record FrameInspection(int frameLength, int logicalLength, int packetId, int firstBodyByte) {
    }

}
