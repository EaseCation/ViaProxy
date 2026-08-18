package net.raphimc.viaproxy.util;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRoutingUtilTest {

    @Test
    void testLoginEntryTargetsMapToReconnectHosts() {
        assertEquals("jetest-login.easecation.net", LoginRoutingUtil.getLoginReconnectHost(InetSocketAddress.createUnresolved("bbdev.easecation.net", 19133)));
        assertEquals("jeprod-login.easecation.net", LoginRoutingUtil.getLoginReconnectHost(InetSocketAddress.createUnresolved("play.easecation.net", 19132)));
    }

    @Test
    void testNonLoginTransferTargetsAreNotRewritten() {
        assertNull(LoginRoutingUtil.getLoginReconnectHost(InetSocketAddress.createUnresolved("bbdev.easecation.net", 25565)));
        assertNull(LoginRoutingUtil.getLoginReconnectHost(InetSocketAddress.createUnresolved("play.easecation.net", 19133)));
        assertNull(LoginRoutingUtil.getLoginReconnectHost(InetSocketAddress.createUnresolved("jetest.easecation.net", 19133)));
        assertNull(LoginRoutingUtil.getLoginReconnectHost(null));
    }

    @Test
    void testLoginEntryHostMatchIsCaseInsensitive() {
        assertEquals("jetest-login.easecation.net", LoginRoutingUtil.getLoginReconnectHost(InetSocketAddress.createUnresolved("BBDEV.EASECATION.NET", 19133)));
        assertEquals("jeprod-login.easecation.net", LoginRoutingUtil.getLoginReconnectHost(InetSocketAddress.createUnresolved("PLAY.easecation.NET", 19132)));
    }

    @Test
    void testHandshakeHostMatchesLoginHosts() {
        assertTrue(LoginRoutingUtil.isJetestLoginHost("jetest-login.easecation.net"));
        assertTrue(LoginRoutingUtil.isJeprodLoginHost("jeprod-login.easecation.net"));
        assertTrue(LoginRoutingUtil.isJetestLoginHost("JETEST-LOGIN.EASECATION.NET"));
        assertFalse(LoginRoutingUtil.isJeprodLoginHost("jetest-login.easecation.net"));
        assertFalse(LoginRoutingUtil.isJetestLoginHost("jetest.easecation.net"));
        assertFalse(LoginRoutingUtil.isJetestLoginHost("play.easecation.net"));
        assertFalse(LoginRoutingUtil.isJetestLoginHost(null));
        assertFalse(LoginRoutingUtil.isJeprodLoginHost(null));
    }

    @Test
    void testLoginBackendSelection() {
        assertEquals("jetest-backend:19133", LoginRoutingUtil.getLoginBackendAddress("jetest-login.easecation.net", "jetest-backend:19133", "jeprod-backend:19132"));
        assertEquals("jeprod-backend:19132", LoginRoutingUtil.getLoginBackendAddress("jeprod-login.easecation.net", "jetest-backend:19133", "jeprod-backend:19132"));
        assertEquals("", LoginRoutingUtil.getLoginBackendAddress("jeprod-login.easecation.net", "jetest-backend:19133", ""));
        assertNull(LoginRoutingUtil.getLoginBackendAddress("play.easecation.net", "jetest-backend:19133", "jeprod-backend:19132"));
        assertNull(LoginRoutingUtil.getLoginBackendAddress(null, "jetest-backend:19133", "jeprod-backend:19132"));
    }

    @Test
    void testLoginHostsMapToOrdinaryEntryHosts() {
        assertEquals("jetest.easecation.net", LoginRoutingUtil.getOrdinaryEntryHost("jetest-login.easecation.net"));
        assertEquals("jeprod.easecation.net", LoginRoutingUtil.getOrdinaryEntryHost("jeprod-login.easecation.net"));
        assertEquals("jetest.easecation.net", LoginRoutingUtil.getOrdinaryEntryHost("JETEST-LOGIN.EASECATION.NET"));
        assertEquals("jeprod.easecation.net", LoginRoutingUtil.getOrdinaryEntryHost("JEPROD-LOGIN.EASECATION.NET"));
        assertNull(LoginRoutingUtil.getOrdinaryEntryHost("jetest.easecation.net"));
        assertNull(LoginRoutingUtil.getOrdinaryEntryHost("jeprod.easecation.net"));
        assertNull(LoginRoutingUtil.getOrdinaryEntryHost("bbdev.easecation.net"));
        assertNull(LoginRoutingUtil.getOrdinaryEntryHost("play.easecation.net"));
        assertNull(LoginRoutingUtil.getOrdinaryEntryHost(null));
    }

}
