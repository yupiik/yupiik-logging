package io.yupiik.logging.jul;

import io.yupiik.logging.jul.handler.StandardHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class YupiikLoggersTest {
    @Test
    void logAndConfigureLoggers() {
        final var buffer = new ByteArrayOutputStream();
        StdTestHandler.current = new PrintStream(buffer);
        System.setProperty(".handlers", StdTestHandler.class.getName());

        final YupiikLoggers previousLoggers = YupiikLoggerFactory.get();
        final YupiikLoggers loggers = new YupiikLoggers();
        YupiikLoggerFactory.set(loggers);
        assertFalse(loggers.getLoggerNames().hasMoreElements());

        final var logger = loggers.getLogger("YupiikLoggersTest.logAndConfigureLoggers", null);
        assertNotNull(logger);

        final var record = new LogRecord(Level.INFO, "foo");
        record.setInstant(Instant.ofEpochMilli(0));
        record.setLoggerName(logger.getName());
        logger.log(record);

        System.clearProperty(".handlers");
        StdTestHandler.current.close();
        StdTestHandler.current = null;
        YupiikLoggerFactory.set(previousLoggers);

        assertEquals(
                "1970-01-01T00:00:00Z [INFO][YupiikLoggersTest.logAndConfigureLoggers] foo",
                buffer.toString(StandardCharsets.UTF_8).trim());
    }

    public static class StdTestHandler extends StandardHandler {
        private static PrintStream current;

        @Override
        protected PrintStream getStream(final LogRecord record) {
            return current;
        }
    }
}
