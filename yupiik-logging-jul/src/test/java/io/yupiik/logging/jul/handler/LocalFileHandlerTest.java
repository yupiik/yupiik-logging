/*
 * Copyright (c) 2021-2022 - Yupiik SAS - https://www.yupiik.com
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static java.lang.Thread.sleep;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LocalFileHandlerTest {
    @Test
    public void logAndRotate() throws IOException {
        final File out = new File("target/LocalFileHandlerTest/logs/");
        cleanup(out);

        final AtomicReference<String> today = new AtomicReference<>();
        final Map<String, String> config = new HashMap<>();

        // initial config
        today.set("day1");
        config.put("filenamePattern", "target/LocalFileHandlerTest/logs/test.%s.%d.log");
        config.put("limit", Long.toString(10 * 1024));
        config.put("level", "INFO");
        config.put("dateCheckInterval", "PT1S");

        final LocalFileHandler handler = new LocalFileHandler() {
            @Override
            protected String currentDate() {
                return today.get();
            }

            @Override
            protected <T> T getProperty(final String name, final Function<String, T> mapper, final Supplier<T> defaultValue) {
                final String s = config.get(name.substring(name.lastIndexOf('.') + 1));
                return s != null ? mapper.apply(s) : defaultValue.get();
            }
        };
        handler.setFormatter(new MessageOnlyFormatter());

        final String string10chars = "abcdefghij";
        final int iterations = 950;
        for (int i = 0; i < iterations; i++) {
            handler.publish(new LogRecord(Level.INFO, string10chars));
        }

        final File[] logFiles = out.listFiles(pathname -> pathname.getName().startsWith("test"));
        final var logFilesNames = new HashSet<>();
        for (final File f : logFiles) {
            logFilesNames.add(f.getName());
        }
        assertEquals(2, logFiles.length);
        assertEquals(Set.of("test.day1.0.log", "test.day1.1.log"), logFilesNames);

        try (final BufferedReader is = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File(out, "test.day1.1.log")), StandardCharsets.UTF_8))) {
            final List<String> lines = is.lines().collect(toList());
            assertEquals(19, lines.size());
            assertEquals(string10chars, lines.iterator().next());
        }

        final long firstFileLen = new File(out, "test.day1.0.log").length();
        assertTrue(firstFileLen >= 1024 * 10 && firstFileLen < 1024 * 10 + (1 + string10chars.getBytes().length));

        // now change of day
        today.set("day2");
        try { // ensure we tets the date
            Thread.sleep(1500);
        } catch (final InterruptedException e) {
            Thread.interrupted();
        }
        handler.publish(new LogRecord(Level.INFO, string10chars));
        assertTrue(new File(out, "test.day2.0.log").exists());

        handler.close();
    }

    @Test
    public void overwrite() throws IOException {
        final File out = new File("target/LocalFileHandlerTest_overwrite/logs/");
        cleanup(out);

        final AtomicReference<String> today = new AtomicReference<>();
        final Map<String, String> config = new HashMap<>();

        // initial config
        today.set("day1");
        config.put("overwrite", "true");
        config.put("truncateIfExists", "true");
        config.put("filenamePattern", "target/LocalFileHandlerTest/logs/test.log");
        config.put("level", "INFO");

        for (int i = 0; i < 3; i++) {
            final LocalFileHandler handler = new LocalFileHandler() {
                @Override
                protected String currentDate() {
                    return today.get();
                }

                @Override
                protected <T> T getProperty(final String name, final Function<String, T> mapper, final Supplier<T> defaultValue) {
                    final String s = config.get(name.substring(name.lastIndexOf('.') + 1));
                    return s != null ? mapper.apply(s) : defaultValue.get();
                }
            };
            handler.setFormatter(new MessageOnlyFormatter());
            handler.publish(new LogRecord(Level.INFO, "data_" + i));
            handler.close();
            assertEquals("data_" + i, Files.readString(Path.of(config.get("filenamePattern"))).strip());
        }
    }

    @Test
    public void purgeMaxArchive(@TempDir final Path temp) throws IOException {
        final var logs = Files.createDirectories(temp.resolve("logs"));

        // initial config
        final var config = new HashMap<String, String>();
        config.put("archiveDirectory", logs.resolve("archives").toString()); // ~immediately for the test
        config.put("archiveOlderThan", "PT0.001S"); // ~immediately for the test
        config.put("maxArchives", "2");
        config.put("filenamePattern", logs.resolve("app.%s.%03d.log").toString());
        config.put("level", "INFO");
        config.put("limit", "6"); // each record will rotate the file

        final var now = new AtomicReference<>(Instant.now());
        final var clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return null;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        final var handler = new LocalFileHandler(clock) {
            @Override
            protected <T> T getProperty(final String name, final Function<String, T> mapper, final Supplier<T> defaultValue) {
                final String s = config.get(name.substring(name.lastIndexOf('.') + 1));
                return s != null ? mapper.apply(s) : defaultValue.get();
            }
        };
        handler.setFormatter(new MessageOnlyEOLFormatter());
        final var expected = List.of(
                List.of("app.2022-09-05.000.log"),
                List.of("app.2022-09-05.000.log.gzip", "app.2022-09-05.001.log"),
                List.of("app.2022-09-05.000.log.gzip", "app.2022-09-05.001.log.gzip", "app.2022-09-05.002.log"),
                List.of("app.2022-09-05.001.log.gzip", "app.2022-09-05.002.log.gzip", "app.2022-09-05.003.log"),
                List.of("app.2022-09-05.002.log.gzip", "app.2022-09-05.003.log.gzip", "app.2022-09-06.000.log")
        ).iterator();
        try {
            for (int i = 0; i < 5; i++) {
                final var debug = "iteration #" + i;
                assertTrue(expected.hasNext(), debug);
                now.set(now.get().plus(i, HOURS));
                handler.publish(new LogRecord(Level.INFO, "data_" + i));
                try (final var list = Files.walk(logs).filter(Files::isRegularFile)) {
                    assertEquals(expected.next(), list.map(Path::getFileName).map(Path::toString).sorted().collect(toList()), debug);
                }
                try {
                    sleep(30);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail(e);
                }
            }
            assertFalse(expected.hasNext());
        } finally {
            handler.close();
        }
    }

    private void cleanup(final File out) {
        if (!out.exists()) {
            return;
        }
        final var files = out.listFiles(pathname -> pathname.getName().startsWith("test"));
        if (files == null) {
            return;
        }
        for (final File file : List.of(files)) {
            if (file != null && !file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    public static class MessageOnlyFormatter extends Formatter {
        @Override
        public String format(final LogRecord record) {
            return record.getMessage() + "\r";
        }
    }

    public static class MessageOnlyEOLFormatter extends Formatter {
        @Override
        public String format(final LogRecord record) {
            return record.getMessage() + "\n";
        }
    }
}
