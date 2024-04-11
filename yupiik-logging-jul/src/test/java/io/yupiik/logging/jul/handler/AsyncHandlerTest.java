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

import io.yupiik.logging.jul.YupiikLoggers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncHandlerTest {
    @AfterEach
    void after() {
        SimpleHandler.RECORDS.clear();
        AsyncTestHandler.conf = null;
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
        final var logger = loggers.getLogger("foo", null);
        logger.info("test");
        // flush
        final var handler = loggers.getLogger("", null).getHandlers()[0];
        handler.close();

        final var records = SimpleHandler.records();
        assertEquals(1, records.size());
        assertEquals("test", records.get(0).getMessage());
    }

    public static class AsyncTestHandler extends AsyncHandler {
        private static Map<String, String> conf;

        @Override
        protected Function<String, String> getPropertySupplier() {
            return conf::get;
        }
    }

    public static class SimpleHandler extends Handler {
        private static final List<LogRecord> RECORDS = new ArrayList<>();

        public synchronized static List<LogRecord> records() {
            return new ArrayList<>(RECORDS);
        }

        @Override
        public synchronized void publish(final LogRecord record) {
            RECORDS.add(record);
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
}
