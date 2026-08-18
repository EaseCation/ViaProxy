package net.raphimc.viaproxy.proxy.client2proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import net.raphimc.netminecraft.constants.ConnectionState;
import net.raphimc.netminecraft.constants.MCPipeline;
import net.raphimc.netminecraft.netty.codec.PacketCodec;
import net.raphimc.netminecraft.netty.codec.PacketSizer;
import net.raphimc.netminecraft.packet.Packet;
import net.raphimc.netminecraft.packet.impl.login.C2SLoginCookieResponsePacket;
import net.raphimc.netminecraft.packet.impl.login.S2CLoginCookieRequestPacket;
import net.raphimc.netminecraft.packet.impl.play.S2CPlayTransferPacket;
import net.raphimc.viaproxy.proxy.packet.S2CPlayStoreCookiePacket;
import net.raphimc.viaproxy.proxy.packet.ViaProxyPacketRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferCookieLoginCodecTest {

    private static final int JAVA_1_21_8_PROTOCOL = 772;

    @Test
    void encodesLoginCookieRequestForJava1218() {
        final S2CLoginCookieRequestPacket decoded = assertInstanceOf(
                S2CLoginCookieRequestPacket.class,
                roundTrip(new S2CLoginCookieRequestPacket("easecation:enter_login"), false, true)
        );

        assertEquals("easecation:enter_login", decoded.key);
    }

    @Test
    void decodesLoginCookieResponseForJava1218() {
        final C2SLoginCookieResponsePacket decoded = assertInstanceOf(
                C2SLoginCookieResponsePacket.class,
                roundTrip(new C2SLoginCookieResponsePacket("easecation:enter_login", new byte[]{1}), true, false)
        );

        assertEquals("easecation:enter_login", decoded.key);
        assertArrayEquals(new byte[]{1}, decoded.payload);
    }

    @Test
    void preservesPlayStoreCookieBeforeTransferForJava1218() {
        assertPlayStoreCookieBeforeTransfer(new byte[]{1});
    }

    @Test
    void preservesEmptyPlayStoreCookieBeforeTransferForJava1218() {
        assertPlayStoreCookieBeforeTransfer(new byte[0]);
    }

    private static void assertPlayStoreCookieBeforeTransfer(final byte[] payload) {
        final EmbeddedChannel outbound = codecChannel(false, ConnectionState.PLAY);
        final ByteBuf cookieFrame;
        final ByteBuf transferFrame;
        try {
            assertTrue(outbound.writeOutbound(new S2CPlayStoreCookiePacket("easecation:enter_login", payload)));
            assertTrue(outbound.writeOutbound(new S2CPlayTransferPacket("bbdev.easecation.net", 25565)));
            cookieFrame = outbound.readOutbound();
            transferFrame = outbound.readOutbound();
        } finally {
            outbound.finishAndReleaseAll();
        }

        final EmbeddedChannel inbound = codecChannel(true, ConnectionState.PLAY);
        try {
            assertTrue(inbound.writeInbound(cookieFrame));
            final S2CPlayStoreCookiePacket cookie = assertInstanceOf(S2CPlayStoreCookiePacket.class, inbound.readInbound());
            assertEquals("easecation:enter_login", cookie.key);
            assertArrayEquals(payload, cookie.payload);

            assertTrue(inbound.writeInbound(transferFrame));
            final S2CPlayTransferPacket transfer = assertInstanceOf(S2CPlayTransferPacket.class, inbound.readInbound());
            assertEquals("bbdev.easecation.net", transfer.host);
            assertEquals(25565, transfer.port);
        } finally {
            inbound.finishAndReleaseAll();
        }
    }

    private static Packet roundTrip(final Packet packet, final boolean outboundServerbound, final boolean inboundServerbound) {
        final EmbeddedChannel outbound = codecChannel(outboundServerbound);
        final ByteBuf frame;
        try {
            assertTrue(outbound.writeOutbound(packet));
            frame = outbound.readOutbound();
        } finally {
            outbound.finishAndReleaseAll();
        }

        final EmbeddedChannel inbound = codecChannel(inboundServerbound);
        try {
            assertTrue(inbound.writeInbound(frame));
            return inbound.readInbound();
        } finally {
            inbound.finishAndReleaseAll();
        }
    }

    private static EmbeddedChannel codecChannel(final boolean serverbound) {
        return codecChannel(serverbound, ConnectionState.LOGIN);
    }

    private static EmbeddedChannel codecChannel(final boolean serverbound, final ConnectionState connectionState) {
        final ViaProxyPacketRegistry registry = new ViaProxyPacketRegistry(serverbound, JAVA_1_21_8_PROTOCOL);
        registry.setConnectionState(connectionState);
        final EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(MCPipeline.PACKET_REGISTRY_ATTRIBUTE_KEY).set(registry);
        channel.pipeline().addLast(MCPipeline.SIZER_HANDLER_NAME, new PacketSizer());
        channel.pipeline().addLast(MCPipeline.PACKET_CODEC_HANDLER_NAME, new PacketCodec());
        return channel;
    }
}
