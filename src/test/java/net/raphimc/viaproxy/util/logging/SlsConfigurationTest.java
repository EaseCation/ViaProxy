package net.raphimc.viaproxy.util.logging;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlsConfigurationTest {

    @Test
    void rejectsIncompleteEnvironment() {
        final Map<String, String> environment = new HashMap<>(completeEnvironment());
        environment.remove("VIAPROXY_SLS_ACCESS_KEY_SECRET");

        assertTrue(SlsConfiguration.fromEnvironment(environment).isEmpty());
        assertEquals(java.util.List.of("VIAPROXY_SLS_ACCESS_KEY_SECRET"), SlsConfiguration.missingVariables(environment));
    }

    @Test
    void loadsCompleteEnvironment() {
        final SlsConfiguration configuration = SlsConfiguration.fromEnvironment(completeEnvironment()).orElseThrow();

        assertEquals("easecation-test", configuration.project());
        assertEquals("viaproxy-exception", configuration.logstore());
        assertEquals("bbdev", configuration.environment());
    }

    static Map<String, String> completeEnvironment() {
        return Map.of(
                "VIAPROXY_SLS_ENDPOINT", "cn-hangzhou.log.aliyuncs.com",
                "VIAPROXY_SLS_PROJECT", "easecation-test",
                "VIAPROXY_SLS_LOGSTORE", "viaproxy-exception",
                "VIAPROXY_SLS_ACCESS_KEY_ID", "test-id",
                "VIAPROXY_SLS_ACCESS_KEY_SECRET", "test-secret",
                "VIAPROXY_ENVIRONMENT", "bbdev",
                "VIAPROXY_POD_NAME", "viaproxy-0",
                "VIAPROXY_NODE_NAME", "node-0"
        );
    }

}
