package net.raphimc.viaproxy.protocoltranslator.viaproxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BedrockFlushConfigurationTest {
    @TempDir Path directory;

    @Test
    void oldConfigurationKeepsTheOriginalInterval() throws Exception {
        assertEquals(10, load("proxy-online-mode: true\n").getBedrockRakNetFlushIntervalMs());
    }

    @Test
    void yamlAcceptsBothConservativeBounds() throws Exception {
        assertEquals(5, load("bedrock-raknet-flush-interval-ms: 5\n").getBedrockRakNetFlushIntervalMs());
        assertEquals(10, load("bedrock-raknet-flush-interval-ms: 10\n").getBedrockRakNetFlushIntervalMs());
    }

    @Test
    void yamlRejectsIntervalsOutsideTheConservativeRange() {
        for (int interval : new int[]{-1, 0, 1, 4, 11, Integer.MAX_VALUE}) {
            assertThrows(RuntimeException.class, () -> load("bedrock-raknet-flush-interval-ms: " + interval + "\n"));
        }
    }

    @Test
    void cliUsesTheSameRangeAndDefault() throws Exception {
        ViaProxyConfig config = new ViaProxyConfig();
        assertEquals(10, config.getBedrockRakNetFlushIntervalMs());
        config.loadFromArguments(new String[]{"--bedrock-raknet-flush-interval-ms", "5"});
        assertEquals(5, config.getBedrockRakNetFlushIntervalMs());
        for (String interval : new String[]{"0", "4", "11"}) {
            assertThrows(IllegalArgumentException.class, () -> new ViaProxyConfig().loadFromArguments(
                    new String[]{"--bedrock-raknet-flush-interval-ms", interval}));
        }
    }

    private ViaProxyConfig load(String yaml) throws Exception {
        Path config = Files.createTempFile(directory, "viaproxy-", ".yml");
        Files.writeString(config, yaml);
        return ViaProxyConfig.create(config.toFile());
    }
}
