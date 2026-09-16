package net.raphimc.viaproxy.plugins.events;

import net.lenni0451.lambdaevents.EventHandler;
import net.raphimc.viaproxy.ViaProxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProxyStartCancellationTest {

    @Test
    void cancelledStartupDoesNotReachBindingAndReleasesTheServerReference() {
        final InvalidConfiguration listener = new InvalidConfiguration();
        ViaProxy.EVENT_MANAGER.register(listener);
        try {
            final IllegalStateException error = assertThrows(IllegalStateException.class, ViaProxy::startProxy);
            assertEquals("Invalid routing configuration", error.getMessage());
            assertNull(ViaProxy.getCurrentProxyServer());
        } finally {
            ViaProxy.EVENT_MANAGER.unregister(listener);
        }
    }

    private static final class InvalidConfiguration {
        @EventHandler
        public void onStart(final ProxyStartEvent event) {
            event.setCancelReason("Invalid routing configuration");
        }
    }
}
