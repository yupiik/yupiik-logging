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

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class JsonFormatter extends Formatter {
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final JsonBuilderFactory builder = Json.createBuilderFactory(Map.of());

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
        final var json = builder.createObjectBuilder();
        if (useUUID) {
            json.add("uuid", UUID.randomUUID().toString());
        }
        json.add("timestamp", OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getMillis()), UTC).toString());
        if (record.getLevel() != null) {
            json.add("level", record.getLevel().getName());
        }
        if (record.getLoggerName() != null) {
            json.add("logger", record.getLoggerName());
        }
        if (record.getSourceMethodName() != null) {
            json.add("method", record.getSourceMethodName());
        }
        final var message = formatMessage ? formatMessage(record) : record.getMessage();
        if (message != null) {
            json.add("message", message);
        }
        if (record.getThrown() != null) {
            json.add("exception", toString(record.getThrown()));
        }
        if (record.getSourceClassName() != null) {
            json.add("class", record.getSourceClassName());
        }
        return json.build().toString() + '\n';
    }

    private String toString(final Throwable thrown) {
        final StringWriter w = new StringWriter();
        try (final PrintWriter writer = new PrintWriter(w)) {
            thrown.printStackTrace(writer);
        }
        return w.toString();
    }
}
