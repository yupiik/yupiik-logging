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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFormatterTest {
    @Test
    void format() {
        final var record = createRecord();
        assertEquals("{\"timestamp\":\"1970-01-01T00:00Z\",\"level\":\"INFO\",\"logger\":\"the.logger\",\"method\":\"the.method\",\"message\":\"test message\",\"exception\":\"java.lang.IllegalArgumentException\\n\\tat this.is.the.Class.methodName(TheFile.java:123)\\n\",\"class\":\"the.source\"}\n", new JsonFormatter().format(record));
    }

    @Test
    void formatWithUuid() {
        final var record = createRecord();
        record.setThrown(null);
        final var jsonFormatter = new JsonFormatter();
        jsonFormatter.setUseUUID(true);
        final var formatted = jsonFormatter.format(record);
        assertTrue(formatted.matches("" +
                "\\{\"uuid\":\".+\"," +
                        "\"timestamp\":\"1970-01-01T00:00Z\",\"level\":\"INFO\"," +
                        "\"logger\":\"the.logger\",\"method\":\"the.method\",\"message\":\"test message\"," +
                        "\"class\":\"the.source\"}\n"),
                formatted);
    }

    private LogRecord createRecord() {
        final LogRecord record = new LogRecord(Level.INFO, "test message");
        record.setLoggerName("the.logger");
        record.setSourceClassName("the.source");
        record.setSourceMethodName("the.method");
        record.setInstant(Instant.ofEpochMilli(0));
        record.setThrown(new IllegalArgumentException());
        record.setThreadID(2);
        record.getThrown().setStackTrace(new StackTraceElement[]{
                new StackTraceElement("this.is.the.Class", "methodName", "TheFile.java", 123)
        });
        return record;
    }
}
