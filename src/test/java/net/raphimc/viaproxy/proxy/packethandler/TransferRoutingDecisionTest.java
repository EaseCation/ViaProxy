package net.raphimc.viaproxy.proxy.packethandler;

import com.google.common.net.HostAndPort;
import net.raphimc.viaproxy.plugins.events.TransferRoutingEvent;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferRoutingDecisionTest {

    @Test
    void eventDefaultsToReconnectThroughViaProxy() {
        TransferRoutingEvent event = new TransferRoutingEvent(null, InetSocketAddress.createUnresolved("pit.example.net", 25577));

        assertEquals(TransferRoutingMode.RECONNECT_THROUGH_VIAPROXY, event.getMode());
        assertTrue(TransferPacketHandler.shouldReconnectThroughViaProxy(event.getMode()));
    }

    @Test
    void directModeKeepsOriginalTarget() {
        TransferRoutingEvent event = new TransferRoutingEvent(null, InetSocketAddress.createUnresolved("pit.example.net", 25577));

        event.setMode(TransferRoutingMode.DIRECT_TO_TARGET);

        assertFalse(TransferPacketHandler.shouldReconnectThroughViaProxy(event.getMode()));
        assertEquals("pit.example.net", event.getOriginalTarget().getHostString());
        assertEquals(25577, event.getOriginalTarget().getPort());
    }

    @Test
    void unknownModeFailsClosed() {
        assertTrue(TransferPacketHandler.shouldReconnectThroughViaProxy(null));
    }

    @Test
    void loginSessionReturnTransferUsesOrdinaryEntryHost() {
        assertEquals("jeprod.easecation.net", TransferPacketHandler.getTransferTargetHost(HostAndPort.fromParts("jeprod-login.easecation.net", 25565), true));
        assertEquals("jetest.easecation.net", TransferPacketHandler.getTransferTargetHost(HostAndPort.fromParts("jetest-login.easecation.net", 25565), true));
    }

    @Test
    void nonLoginSessionTransferHostIsUnchanged() {
        assertEquals("jeprod.easecation.net", TransferPacketHandler.getTransferTargetHost(HostAndPort.fromParts("jeprod.easecation.net", 25565), true));
        assertEquals("bbdev.easecation.net", TransferPacketHandler.getTransferTargetHost(HostAndPort.fromParts("bbdev.easecation.net", 25565), true));
    }

    @Test
    void loginRoutingDisabledKeepsOriginalHandshakeHost() {
        assertEquals("jeprod-login.easecation.net", TransferPacketHandler.getTransferTargetHost(HostAndPort.fromParts("jeprod-login.easecation.net", 25565), false));
        assertNull(TransferPacketHandler.getTransferTargetHost(null, true));
    }
}
