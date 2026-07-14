package net.raphimc.viaproxy.util.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlsAppenderTest {

    @Test
    void asynchronouslyAcceptsOnlyErrorAndFatalEvents() throws Exception {
        final RecordingTransport transport = new RecordingTransport(2);
        final AsyncSlsReporter reporter = new AsyncSlsReporter(transport, System.err);
        final SlsAppender appender = SlsAppender.createForTesting("SlsTest", reporter, configuration());
        appender.start();

        appender.append(event(Level.INFO, "info"));
        appender.append(event(Level.WARN, "warn"));
        appender.append(event(Level.ERROR, "error"));
        appender.append(event(Level.FATAL, "fatal"));

        assertTrue(transport.await(2, TimeUnit.SECONDS));
        appender.stop(2, TimeUnit.SECONDS);
        assertEquals(List.of("error", "fatal"), transport.messages());
        assertTrue(transport.closed);
    }

    @Test
    void transportFailureDoesNotEscapeOrReenterLogging() {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final PrintStream diagnostics = new PrintStream(output, true, StandardCharsets.UTF_8);
        final SlsTransport failingTransport = new SlsTransport() {
            @Override
            public void send(final List<SlsLogRecord> records) {
                throw new IllegalStateException("offline");
            }

            @Override
            public void close() {
            }
        };
        final AsyncSlsReporter reporter = new AsyncSlsReporter(failingTransport, diagnostics);
        final SlsAppender appender = SlsAppender.createForTesting("SlsTest", reporter, configuration());
        appender.start();

        assertDoesNotThrow(() -> appender.append(event(Level.ERROR, "error")));
        appender.stop(2, TimeUnit.SECONDS);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("[ViaProxy SLS] Upload failed: IllegalStateException: offline"));
    }

    private static Log4jLogEvent event(final Level level, final String message) {
        return Log4jLogEvent.newBuilder()
                .setLevel(level)
                .setLoggerName("ViaProxy")
                .setThreadName("test")
                .setMessage(new SimpleMessage(message))
                .build();
    }

    private static SlsConfiguration configuration() {
        return SlsConfiguration.fromEnvironment(SlsConfigurationTest.completeEnvironment()).orElseThrow();
    }

    private static final class RecordingTransport implements SlsTransport {

        private final List<SlsLogRecord> records = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch latch;
        private volatile boolean closed;

        private RecordingTransport(final int expectedRecords) {
            this.latch = new CountDownLatch(expectedRecords);
        }

        @Override
        public void send(final List<SlsLogRecord> records) {
            this.records.addAll(records);
            records.forEach(ignored -> this.latch.countDown());
        }

        @Override
        public void close() {
            this.closed = true;
        }

        private boolean await(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
            return this.latch.await(timeout, timeUnit);
        }

        private List<String> messages() {
            synchronized (this.records) {
                return this.records.stream().map(record -> record.fields().get("message")).toList();
            }
        }

    }

}
