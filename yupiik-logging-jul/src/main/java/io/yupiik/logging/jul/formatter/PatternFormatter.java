/*
 * Copyright (c) 2021 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.logging.jul.formatter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import static java.util.stream.Collectors.joining;

public class PatternFormatter extends Formatter {
    private final Collection<Item> items;

    public PatternFormatter(final String pattern) {
        this.items = parse(pattern);
    }

    @Override
    public String format(final LogRecord record) {
        return items.stream().map(i -> i.extract(this, record)).collect(joining());
    }

    private static Collection<Item> parse(final String pattern) {
        final Collection<Item> items = new ArrayList<>();
        final var builder = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            final char current = pattern.charAt(i);
            switch (current) {
                case '%':
                    final char marker = pattern.charAt(i + 1);
                    switch (marker) {
                        case 'n':
                            i++;
                            flushBuilder(items, builder);
                            items.add(new Constant("\n"));
                            break;
                        case 'C':
                            i++;
                            flushBuilder(items, builder);
                            items.add(new ClassName());
                            break;
                        case 'M':
                            i++;
                            flushBuilder(items, builder);
                            items.add(new MethodName());
                            break;
                        case 'l':
                            i++;
                            flushBuilder(items, builder);
                            if (pattern.substring(i).startsWith("logger")) {
                                items.add(new LoggerName());
                                i += "logger".length() - 1;
                            } else {
                                items.add(new Level());
                                i = eatIf("level", pattern, i); // %level alias
                            }
                            break;
                        case 'm':
                            i++;
                            flushBuilder(items, builder);
                            if (pattern.substring(i).startsWith("method")) {
                                items.add(new MethodName());
                                i += "method".length() - 1;
                            } else {
                                items.add(new Message());
                                i = eatIf("message", pattern, i);
                            }
                            break;
                        case 'd':
                            i++;
                            flushBuilder(items, builder);
                            i = eatIf("date", pattern, i);
                            if (pattern.length() > i + 1 && pattern.charAt(i + 1) == '{') {
                                final int end = pattern.indexOf('}', i);
                                items.add(new Date(DateTimeFormatter.ofPattern(pattern.substring(i + 2, end))));
                                i = end;
                            } else {
                                items.add(new Date(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                            }
                            break;
                        case 'c':
                            i++;
                            flushBuilder(items, builder);
                            if (pattern.substring(i).startsWith("class")) {
                                items.add(new ClassName());
                                i += "class".length() - 1;
                            } else {
                                items.add(new LoggerName());
                            }
                            break;
                        case 'x':
                            i++;
                            flushBuilder(items, builder);
                            items.add(new Exception());
                            break;
                        case 'T':
                            i++;
                            flushBuilder(items, builder);
                            items.add(new ThreadId());
                            break;
                        case 't': {
                            i++;
                            flushBuilder(items, builder);
                            final int start = i;
                            i = eatIf("thread", pattern, i);
                            if (i == start) {
                                items.add(new ThreadName());
                                break;
                            }
                            i++;
                            final int startThread = i;
                            i = eatIf("Name", pattern, i);
                            if (startThread != i) {
                                items.add(new ThreadName());
                            } else {
                                i = eatIf("Id", pattern, i);
                                if (i != startThread) {
                                    items.add(new ThreadId());
                                } else {
                                    throw new IllegalArgumentException("Only %threadId or %threadName can start with %t");
                                }
                            }
                            break;
                        }
                        case 'u': {
                            i++;
                            flushBuilder(items, builder);
                            final int start = i;
                            i = eatIf("uuid", pattern, i);
                            if (i == start) {
                                throw new IllegalArgumentException("Only %uuid can start with %u");
                            }
                            items.add(new Uuid());
                            break;
                        }
                        case 'e': {
                            i++;
                            flushBuilder(items, builder);
                            final int start = i;
                            i = eatIf("exception", pattern, i);
                            if (i == start) {
                                throw new IllegalArgumentException("Only %exception can start with %e");
                            }
                            items.add(new Exception());
                            break;
                        }
                        case '%': // to output '%' you use '%%'
                            i++;
                            builder.append(current);
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported item: '%" + marker + "'");
                    }
                    break;
                default:
                    builder.append(current);
            }
        }
        flushBuilder(items, builder);
        return items;
    }

    private static int eatIf(final String value, final String pattern, final int i) {
        if (pattern.substring(i).startsWith(value)) {
            return i + value.length() - 1;
        }
        return i;
    }

    private static void flushBuilder(final Collection<Item> items, final StringBuilder builder) {
        if (builder.length() > 0) {
            items.add(new Constant(builder.toString()));
            builder.setLength(0);
        }
    }

    private interface Item {
        String extract(Formatter formatter, LogRecord record);
    }

    private static class Constant implements Item {
        private final String value;

        private Constant(final String value) {
            this.value = value;
        }

        @Override
        public String extract(final Formatter formatter, final LogRecord record) {
            return value;
        }
    }

    private static class LoggerName implements Item {
        @Override
        public String extract(final Formatter formatter, final LogRecord record) {
            return record.getLoggerName();
        }
    }

    private static class ClassName implements Item {
        @Override
        public String extract(final Formatter formatter, final LogRecord record) {
            final var value = record.getSourceClassName();
            return value == null ? "" : value;
        }
    }

    private static class MethodName implements Item {
        @Override
        public String extract(final Formatter formatter, final LogRecord record) {
            final var value = record.getSourceMethodName();
            return value == null ? "" : value;
        }
    }

    private static class ThreadId implements Item {
        @Override
        public String extract(final Formatter formatter, final LogRecord record) {
            return Integer.toString(record.getThreadID());
        }
    }

    private static class ThreadName implements Item {
        @Override
        public String extract(final Formatter formatter, final LogRecord record) {
            return Thread.currentThread().getName();
        }
    }

    private static class Level implements Item {
        @Override
        public String extract(final Formatter formatter, final LogRecord record) {
            return record.getLevel().getName();
        }
    }

    private static class Message implements Item {
        @Override
        public String extract(final Formatter formatter, final LogRecord record) {
            return formatter.formatMessage(record);
        }
    }

    private static class Exception implements Item {
        @Override
        public String extract(final Formatter formatter, final LogRecord record) {
            final var thrown = record.getThrown();
            if (thrown == null) {
                return "";
            }
            final StringWriter writer = new StringWriter();
            try (final PrintWriter printWriter = new PrintWriter(writer)) {
                thrown.printStackTrace(printWriter);
            }
            return '\n' + writer.toString().trim();
        }
    }

    private static class Uuid implements Item {
        @Override
        public String extract(final Formatter formatter, final LogRecord record) {
            return UUID.randomUUID().toString();
        }
    }

    private static class Date implements Item {
        private static final ZoneId UTC = ZoneId.of("UTC");

        private final DateTimeFormatter formatter;

        private Date(final DateTimeFormatter formatter) {
            this.formatter = formatter;
        }

        @Override
        public String extract(final Formatter formatter, final LogRecord record) {
            return OffsetDateTime.ofInstant(record.getInstant(), UTC).format(this.formatter);
        }
    }
}
