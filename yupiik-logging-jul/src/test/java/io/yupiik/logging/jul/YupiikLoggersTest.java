/*
 * Copyright (c) 2021-present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.logging.jul;

import io.yupiik.logging.jul.formatter.JsonFormatter;
import io.yupiik.logging.jul.handler.StandardHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                "1970-01-01T00:00:00.000Z [INFO][YupiikLoggersTest.logAndConfigureLoggers] foo",
                buffer.toString(StandardCharsets.UTF_8).trim());
    }

    @Test
    void jsonFormatterAdditionalFieldsFromConfig() {
        final var conf = Map.of(
                TestHandler.class.getName() + ".formatter",
                "json(useUUID=false;field.service.name=my-app;field.service.version=1.2.3;field.query=a=b)");
        final var loggers = new YupiikLoggers() {
            @Override
            public String getProperty(final String name) {
                return conf.get(name);
            }
        };

        final var handler = new TestHandler();
        loggers.initHandler(TestHandler.class.getName(), handler);

        assertInstanceOf(JsonFormatter.class, handler.getFormatter(), () -> String.valueOf(handler.getFormatter()));

        final var record = new LogRecord(Level.INFO, "hello");
        record.setInstant(Instant.ofEpochMilli(0));
        record.setLoggerName("the.logger");

        final var formatted = handler.getFormatter().format(record);
        assertTrue(formatted.startsWith("{\"timestamp\":\"1970-01-01T00:00Z\",\"level\":\"INFO\",\"logger\":\"the.logger\",\"message\":\"hello\","), formatted);
        assertTrue(formatted.contains("\"service.name\":\"my-app\""), formatted);
        assertTrue(formatted.contains("\"service.version\":\"1.2.3\""), formatted);
        assertTrue(formatted.contains("\"query\":\"a=b\""), formatted);
        assertTrue(formatted.endsWith("}\n"), formatted);
    }

    public static class TestHandler extends Handler {
        @Override
        public void publish(final LogRecord record) {
            // no-op
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() throws SecurityException {
            flush();
        }
    }

    public static class StdTestHandler extends StandardHandler {
        private static PrintStream current;

        @Override
        protected PrintStream getStream(final LogRecord record) {
            return current;
        }
    }
}
