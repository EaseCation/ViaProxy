package net.raphimc.viaproxy.plugins.events;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.raphimc.netminecraft.constants.IntendedState;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferCookieEventsTest {

    @Test
    void reconnectCookiePayloadIsDefensivelyCopied() {
        final TransferRoutingEvent event = new TransferRoutingEvent(null, InetSocketAddress.createUnresolved("play.example.net", 19132));
        final byte[] payload = new byte[]{1};

        event.setReconnectCookie("example:route", payload);
        payload[0] = 0;

        assertEquals("example:route", event.getReconnectCookieKey());
        assertArrayEquals(new byte[]{1}, event.getReconnectCookiePayload());
        final byte[] returned = event.getReconnectCookiePayload();
        returned[0] = 0;
        assertArrayEquals(new byte[]{1}, event.getReconnectCookiePayload());
    }

    @Test
    void preConnectOnlyReturnsTheRequestedCookie() {
        final PreConnectEvent event = new PreConnectEvent(
                InetSocketAddress.createUnresolved("default.example.net", 19132),
                ProtocolVersion.v1_21_5,
                ProtocolVersion.v1_21_5,
                null,
                IntendedState.TRANSFER,
                null,
                "example:route",
                new byte[]{1}
        );

        assertArrayEquals(new byte[]{1}, event.getTransferCookie("example:route"));
        assertEquals(null, event.getTransferCookie("example:other"));
    }

    @Test
    void onlyOneTransferCookieCanBeRequested() {
        final TransferCookieRequestEvent event = new TransferCookieRequestEvent(ProtocolVersion.v1_21_5, null, null);

        event.requestCookie("example:route");
        event.requestCookie("example:route");

        assertEquals("example:route", event.getCookieKey());
        assertThrows(IllegalStateException.class, () -> event.requestCookie("example:other"));
    }
}
