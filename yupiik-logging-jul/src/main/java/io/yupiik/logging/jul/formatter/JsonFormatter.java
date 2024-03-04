/*
 * Copyright (c) 2021-2023 - Yupiik SAS - https://www.yupiik.com
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class JsonFormatter extends Formatter {
    private static final ZoneId UTC = ZoneId.of("UTC");

    private boolean useUUID;
    private boolean formatMessage = true;
    private Function<LogRecord, Map<String, String>> customEntriesMapper = null;

    public void setCustomEntriesMapper(final Function<LogRecord, Map<String, String>> customEntriesMapper) {
        this.customEntriesMapper = customEntriesMapper;
    }

    public void setFormatMessage(final boolean formatMessage) {
        this.formatMessage = formatMessage;
    }

    public void setUseUUID(final boolean useUUID) {
        this.useUUID = useUUID;
    }

    @Override
    public String format(final LogRecord record) {
        final var json = new StringBuilder("{");
        if (useUUID) {
            json.append("\"uuid\":\"").append(UUID.randomUUID()).append("\",");
        }
        json.append("\"timestamp\":").append(escape(OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getMillis()), UTC).toString()));
        if (record.getLevel() != null) {
            json.append(",\"level\":\"").append(record.getLevel().getName()).append("\"");
        }
        if (record.getLoggerName() != null) {
            json.append(",\"logger\":\"").append(record.getLoggerName()).append("\"");
        }
        if (record.getSourceMethodName() != null) {
            json.append(",\"method\":\"").append(record.getSourceMethodName()).append("\"");
        }
        final var message = formatMessage ? formatMessage(record) : record.getMessage();
        if (message != null) {
            json.append(",\"message\":").append(escape(message));
        }
        if (record.getThrown() != null) {
            json.append(",\"exception\":").append(escape(toString(record.getThrown())));
        }
        if (record.getSourceClassName() != null) {
            json.append(",\"class\":\"").append(record.getSourceClassName()).append("\"");
        }
        if (customEntriesMapper != null) {
            final var data = customEntriesMapper.apply(record);
            if (data != null) {
                data.forEach((k, v) -> json.append(",\"").append(k).append("\":").append(v));
            }
        }
        return json.append('}').toString() + '\n';
    }

    protected String escape(final String value) {
        return JsonStrings.escape(value);
    }

    protected String toString(final Throwable thrown) {
        final var w = new StringWriter();
        try (final PrintWriter writer = new PrintWriter(w)) {
            thrown.printStackTrace(writer);
        }
        return w.toString();
    }
}
