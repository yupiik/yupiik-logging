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

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbPropertyOrder;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class JsonFormatter extends Formatter {
    private final Jsonb mapper = JsonbBuilder.create(new JsonbConfig().setProperty("johnzon.skip-cdi", true));

    private boolean useUUID;
    private boolean formatMessage = true;

    public void setFormatMessage(final boolean formatMessage) {
        this.formatMessage = formatMessage;
    }

    public void setUseUUID(final boolean useUUID) {
        this.useUUID = useUUID;
    }

    @Override
    public String format(final LogRecord record) {
        final var object = new Record(record, formatMessage ? formatMessage(record) : record.getMessage());
        if (useUUID) {
            object.uuid = UUID.randomUUID().toString();
        }
        return mapper.toJson(object) + '\n';
    }

    @JsonbPropertyOrder({
            "uuid",
            "timestamp",
            "level",
            "logger",
            "clazz",
            "method",
            "message",
            "exception"
    })
    public static class Record {
        private static final ZoneId UTC = ZoneId.of("UTC");

        @JsonbProperty("class")
        private final String clazz;
        private final String method;
        private final String message;
        private final String logger;
        private final String exception;
        private final String level;
        private final OffsetDateTime timestamp;
        private String uuid;

        private Record(final LogRecord record, final String message) {
            this.timestamp = OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getMillis()), UTC);
            this.level = record.getLevel().getName();
            this.logger = record.getLoggerName();
            this.clazz = record.getSourceClassName();
            this.method = record.getSourceMethodName();
            this.message = message;
            this.exception = record.getThrown() == null ? null : toString(record.getThrown());
        }

        public String getUuid() {
            return uuid;
        }

        public String getClazz() {
            return clazz;
        }

        public String getMethod() {
            return method;
        }

        public String getMessage() {
            return message;
        }

        public String getLogger() {
            return logger;
        }

        public String getException() {
            return exception;
        }

        public String getLevel() {
            return level;
        }

        public OffsetDateTime getTimestamp() {
            return timestamp;
        }

        private String toString(final Throwable thrown) {
            final StringWriter w = new StringWriter();
            try (final PrintWriter writer = new PrintWriter(w)) {
                thrown.printStackTrace(writer);
            }
            return w.toString();
        }
    }
}
