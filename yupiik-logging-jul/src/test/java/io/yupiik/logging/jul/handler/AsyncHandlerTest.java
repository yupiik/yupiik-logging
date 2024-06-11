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
package io.yupiik.logging.jul.handler;

import io.yupiik.logging.jul.YupiikLoggerFactory;
import io.yupiik.logging.jul.YupiikLoggers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncHandlerTest {
    private YupiikLoggers oldLoggers;

    @BeforeEach
    void init() {
        oldLoggers = YupiikLoggerFactory.unsafeGet();
    }

    @AfterEach
    void after() {
        SimpleHandler.RECORDS.clear();
        SimpleFormattedHandler.RECORDS.clear();
        AsyncTestHandler.conf = null;
        YupiikLoggerFactory.unsafeSet(oldLoggers);
    }

    @Test
    void test() {
        final var conf = Map.of(
                ".handlers", AsyncTestHandler.class.getName(),
                AsyncHandler.class.getName() + ".delegate.class", SimpleHandler.class.getName()
        );
        AsyncTestHandler.conf = conf;
        final var loggers = new YupiikLoggers() {
            @Override
            public String getProperty(final String name) {
                return conf.get(name);
            }
        };
        YupiikLoggerFactory.unsafeSet(loggers);
        final var logger = loggers.getLogger("foo", null);
        logger.info("test");
        // flush
        final var handler = loggers.getLogger("", null).getHandlers()[0];
        handler.close();

        final var records = SimpleHandler.records();
        assertEquals(1, records.size());
        assertEquals("test", records.get(0).getMessage());
    }

    @Test
    void asyncContext() {
        final var conf = Map.of(
                ".handlers", AsyncTestHandler.class.getName(),
                AsyncHandler.class.getName() + ".delegate.class", SimpleFormattedHandler.class.getName(),
                AsyncTestHandler.class.getName() + ".formatter", "json(customEntriesMapper=" + ContextEnricher.class.getName() + ")"
        );
        AsyncTestHandler.conf = conf;
        final var loggers = new YupiikLoggers() {
            @Override
            public String getProperty(final String name) {
                return conf.get(name);
            }
        };
        YupiikLoggerFactory.unsafeSet(loggers);
        ContextEnricher.CTX.set(Map.of("custom_source", "\"asyncContextTest\""));
        try {
            final var logger = loggers.getLogger("foo", null);
            logger.info("test");
        } finally {
            ContextEnricher.CTX.remove();
        }
        // flush
        final var handler = loggers.getLogger("", null).getHandlers()[0];
        handler.close();

        final var records = SimpleFormattedHandler.records();
        assertEquals(1, records.size());
        assertEquals(
                "\"level\":\"INFO\",\"logger\":\"foo\",\"method\":\"log\",\"message\":\"test\",\"class\":\"io.yupiik.logging.jul.logger.YupiikLogger\",\"custom_source\":\"asyncContextTest\"}\n",
                records.get(0).substring(records.get(0).indexOf(',') + 1));
    }

    public static class ContextEnricher implements Function<LogRecord, Map<String, String>> {
        private static final ThreadLocal<Map<String, String>> CTX = new ThreadLocal<>();

        @Override
        public Map<String, String> apply(final LogRecord logRecord) {
            return ofNullable(CTX.get()).orElseGet(Map::of);
        }
    }

    public static class AsyncTestHandler extends AsyncHandler {
        private static Map<String, String> conf;

        @Override
        protected Function<String, String> getPropertySupplier() {
            return conf::get;
        }
    }

    public static abstract class BaseHandler extends Handler {
        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() throws SecurityException {
            flush();
        }
    }

    public static class SimpleFormattedHandler extends BaseHandler {
        private static final List<String> RECORDS = new ArrayList<>();

        public synchronized static List<String> records() {
            return new ArrayList<>(RECORDS);
        }

        @Override
        public synchronized void publish(final LogRecord record) {
            RECORDS.add(getFormatter().format(record));
        }
    }

    public static class SimpleHandler extends BaseHandler {
        private static final List<LogRecord> RECORDS = new ArrayList<>();

        public synchronized static List<LogRecord> records() {
            return new ArrayList<>(RECORDS);
        }

        @Override
        public synchronized void publish(final LogRecord record) {
            RECORDS.add(record);
        }
    }
}
