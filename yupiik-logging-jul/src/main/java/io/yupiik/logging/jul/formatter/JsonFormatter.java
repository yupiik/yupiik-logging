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
package io.yupiik.logging.jul.formatter;

import io.yupiik.logging.jul.api.RecordFreezer;

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

public class JsonFormatter extends Formatter implements RecordFreezer {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String NAME = JsonFormatter.class.getName();

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
    public LogRecord apply(final LogRecord logRecord) {
        final var mapper = customEntriesMapper;
        if (mapper == null) {
            return logRecord;
        }
        return new FrozenLogRecord<>(logRecord, customEntriesMapper.apply(logRecord), NAME);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String format(final LogRecord record) {
        final var json = new StringBuilder("{");
        if (useUUID) {
            json.append("\"uuid\":\"").append(UUID.randomUUID()).append("\",");
        }
        json.append("\"timestamp\":").append(simpleEscape(OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getMillis()), UTC).toString()));
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
            json.append(",\"message\":").append(simpleEscape(message));
        }
        if (record.getThrown() != null) {
            json.append(",\"exception\":").append(simpleEscape(toString(record.getThrown())));
        }
        if (record.getSourceClassName() != null) {
            json.append(",\"class\":\"").append(record.getSourceClassName()).append("\"");
        }
        if (record instanceof FrozenLogRecord<?>) {
            var flr = (FrozenLogRecord<?>) record;
            while (!NAME.equals(flr.getCreator())) {
                if (flr.getDelegate() instanceof FrozenLogRecord<?>) {
                    flr = (FrozenLogRecord<?>) flr.getDelegate();
                } else {
                    flr = null;
                    break;
                }
            }
            if (flr == null) {
                appendMapperEnrichment(record, json);
            } else if (flr.getData() instanceof Map<?, ?>){
                appendCustomData(json, (Map<String, String>) flr.getData());
            }
        } else {
            appendMapperEnrichment(record, json);
        }
        return json.append('}').toString() + '\n';
    }

    private void appendMapperEnrichment(final LogRecord record, final StringBuilder json) {
        if (customEntriesMapper != null) {
            appendCustomData(json, customEntriesMapper.apply(record));
        }
    }

    private void appendCustomData(final StringBuilder json, final Map<String, String> data) {
        if (data != null) {
            data.forEach((k, v) -> json.append(",\"").append(k).append("\":").append(v));
        }
    }

    protected StringBuilder simpleEscape(final String value) {
        return JsonStrings.escape(value);
    }

    @Deprecated // kept for backward compatibility - classes extending this one
    protected String escape(final String value) {
        return JsonStrings.escape(value).toString();
    }

    protected String toString(final Throwable thrown) {
        final var w = new StringWriter();
        try (final PrintWriter writer = new PrintWriter(w)) {
            thrown.printStackTrace(writer);
        }
        return w.toString();
    }
}
