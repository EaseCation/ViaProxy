package net.raphimc.viaproxy.util.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlsLogRecordTest {

    @Test
    void convertsThrowableAndMetadataWithoutChangingEventTime() {
        final IllegalStateException error = new IllegalStateException("failed for 10.0.0.5");
        final Log4jLogEvent event = Log4jLogEvent.newBuilder()
                .setLevel(Level.ERROR)
                .setLoggerName("ViaProxy")
                .setThreadName("Netty Worker")
                .setMessage(new SimpleMessage("peer 192.168.1.8 failed"))
                .setThrown(error)
                .setTimeMillis(1_720_000_123_456L)
                .build();

        final SlsLogRecord record = SlsLogRecord.from(event, configuration(), true);

        assertEquals(1_720_000_123_456L, record.timestampMillis());
        assertEquals("viaproxy", record.fields().get("app"));
        assertEquals("bbdev", record.fields().get("environment"));
        assertEquals("ERROR", record.fields().get("level"));
        assertEquals("ViaProxy", record.fields().get("logger"));
        assertEquals("Netty Worker", record.fields().get("thread"));
        assertEquals("peer REDACTED_IP failed", record.fields().get("message"));
        assertEquals(IllegalStateException.class.getName(), record.fields().get("exceptionClass"));
        assertTrue(record.fields().get("exception").contains("IllegalStateException: failed for REDACTED_IP"));
        assertFalse(record.fields().get("exception").contains("10.0.0.5"));
        assertEquals("false", record.fields().get("error"));
        assertEquals("viaproxy-0", record.fields().get("pod"));
        assertEquals("node-0", record.fields().get("node"));
    }

    @Test
    void supportsMessageOnlyErrors() {
        final Log4jLogEvent event = Log4jLogEvent.newBuilder()
                .setLevel(Level.FATAL)
                .setMessage(new SimpleMessage("fatal without throwable"))
                .build();

        final SlsLogRecord record = SlsLogRecord.from(event, configuration(), false);

        assertEquals("FATAL", record.fields().get("level"));
        assertEquals("fatal without throwable", record.fields().get("message"));
        assertEquals("", record.fields().get("exceptionClass"));
        assertEquals("", record.fields().get("exception"));
    }

    private static SlsConfiguration configuration() {
        return SlsConfiguration.fromEnvironment(SlsConfigurationTest.completeEnvironment()).orElseThrow();
    }

}
