package net.raphimc.viaproxy.proxy.packethandler;

import com.google.common.net.HostAndPort;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import net.lenni0451.lambdaevents.EventHandler;
import net.raphimc.netminecraft.packet.impl.common.S2CTransferPacket;
import net.raphimc.netminecraft.packet.impl.play.S2CPlayTransferPacket;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.plugins.events.TransferRoutingEvent;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.proxy.util.TransferDataHolder;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TransferRoutingDecisionTest {

    @Test
    void directRewriteChangesTheActualPacketWithoutResolvingOrCachingTheTarget() {
        withConnection(event -> {
            assertEquals("old.invalid", event.getOriginalTarget().getHostString());
            event.setRouting(TransferRoutingMode.DIRECT_TO_TARGET, address("new.invalid", 25577), null);
        }, connection -> {
            final S2CTransferPacket packet = new S2CPlayTransferPacket("old.invalid", 19134);
            assertTrue(new TransferPacketHandler(connection).handleP2S(packet, List.of()));
            assertEquals("new.invalid", packet.host);
            assertEquals(25577, packet.port);
            assertFalse(TransferDataHolder.hasTempRedirect(connection.getC2P()));
        });
    }

    @Test
    void reconnectCachesTheBackendButSendsTheIndependentPublicEntry() {
        withConnection(event -> event.setRouting(TransferRoutingMode.RECONNECT_THROUGH_VIAPROXY,
                address("127.0.0.1", 25565), address("public-entry.invalid", 25571)), connection -> {
            final S2CTransferPacket packet = new S2CPlayTransferPacket("old.invalid", 19134);
            new TransferPacketHandler(connection).handleP2S(packet, List.of());
            assertEquals("public-entry.invalid", packet.host);
            assertEquals(25571, packet.port);
            final InetSocketAddress cached = TransferDataHolder.removeTempRedirect(connection.getC2P());
            assertEquals("127.0.0.1", cached.getHostString());
            assertEquals(25565, cached.getPort());
        });
    }

    @Test
    void unmatchedTransferKeepsTheOriginalBackendAndNonstandardEntryPort() {
        withConnection(event -> {}, connection -> {
            final S2CTransferPacket packet = new S2CPlayTransferPacket("127.0.0.1", 19133);
            new TransferPacketHandler(connection).handleP2S(packet, List.of());
            assertEquals("entry.invalid", packet.host);
            assertEquals(25566, packet.port);
            assertEquals(19133, TransferDataHolder.removeTempRedirect(connection.getC2P()).getPort());
        });
    }

    @Test
    void legacyModeOnlyListenerStillForwardsTheOriginalTarget() {
        withConnection(event -> event.setMode(TransferRoutingMode.DIRECT_TO_TARGET), connection -> {
            final S2CTransferPacket packet = new S2CPlayTransferPacket("original.invalid", 25577);
            new TransferPacketHandler(connection).handleP2S(packet, List.of());
            assertEquals("original.invalid", packet.host);
            assertEquals(25577, packet.port);
            assertFalse(TransferDataHolder.hasTempRedirect(connection.getC2P()));
        });
    }

    @Test
    void invalidDecisionDoesNotPartiallyOverwriteAnExistingDecision() {
        final TransferRoutingEvent event = new TransferRoutingEvent(null, address("original.invalid", 19132));
        event.setTargetHost("selected.invalid");
        event.setReconnectAddress(address("entry.invalid", 25565));
        assertThrows(IllegalArgumentException.class, () -> event.setRouting(TransferRoutingMode.DIRECT_TO_TARGET,
                address("other.invalid", 25577), address("invalid.invalid", 0)));
        assertEquals(TransferRoutingMode.RECONNECT_THROUGH_VIAPROXY, event.getMode());
        assertEquals("selected.invalid", event.getTarget().getHostString());
        assertEquals(19132, event.getTarget().getPort());
        assertEquals("entry.invalid", event.getReconnectAddress().getHostString());
        event.setTargetPort(25578);
        assertEquals("selected.invalid", event.getTarget().getHostString());
        assertEquals(25578, event.getTarget().getPort());
        assertEquals("original.invalid", event.getOriginalTarget().getHostString());
        assertEquals(19132, event.getOriginalTarget().getPort());
    }

    private static InetSocketAddress address(final String host, final int port) {
        return InetSocketAddress.createUnresolved(host, port);
    }

    private static void withConnection(final Consumer<TransferRoutingEvent> routing, final Consumer<ProxyConnection> verify) {
        final EmbeddedChannel channel = new EmbeddedChannel() {
            @Override
            protected SocketAddress remoteAddress0() {
                return new InetSocketAddress("192.0.2.80", 30000);
            }
        };
        final ProxyConnection connection = new ProxyConnection(new ChannelInitializer<>() {
            @Override
            protected void initChannel(final Channel channel) {
            }
        }, channel);
        connection.setClientHandshakeAddress(HostAndPort.fromParts("entry.invalid", 25566));
        final TestRoutingListener listener = new TestRoutingListener(routing);
        TransferDataHolder.removeTempRedirect(channel);
        ViaProxy.EVENT_MANAGER.register(listener);
        try {
            verify.accept(connection);
        } finally {
            ViaProxy.EVENT_MANAGER.unregister(listener);
            TransferDataHolder.removeTempRedirect(channel);
            channel.finishAndReleaseAll();
        }
    }

    private record TestRoutingListener(Consumer<TransferRoutingEvent> routing) {
        @EventHandler
        public void onTransfer(final TransferRoutingEvent event) {
            this.routing.accept(event);
        }
    }
}
