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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternFormatterTest {
    @ParameterizedTest
    @CsvSource({
            "%m,test message",
            "%message,test message",
            "%l,INFO",
            "%level,INFO",
            "%%,%",
            "%n,'\n'",
            "%c,the.logger",
            "%logger,the.logger",
            "%C,the.source",
            "%class,the.source",
            "%M,the.method",
            "%method,the.method",
            "%d,1970-01-01T00:00:00Z",
            "%date,1970-01-01T00:00:00Z",
            "%d{yyyy-MM-dd HH:mm:ss},1970-01-01 00:00:00",
            "%date{yyyy-MM-dd HH:mm:ss},1970-01-01 00:00:00",
            "%x,'\njava.lang.IllegalArgumentException\n\tat this.is.the.Class.methodName(TheFile.java:123)'",
            "%exception,'\njava.lang.IllegalArgumentException\n\tat this.is.the.Class.methodName(TheFile.java:123)'",
            "%T,2",
            "%threadId,2",
            "%t,main",
            "%threadName,main",
            // complex
            "%d [%l][%c][%C][%M] %m%x%n,'1970-01-01T00:00:00Z [INFO][the.logger][the.source][the.method] test message\njava.lang.IllegalArgumentException\n\tat this.is.the.Class.methodName(TheFile.java:123)\n'",
    })
    void format(final String pattern, final String output) {
        final var record = new LogRecord(Level.INFO, "test message");
        record.setLoggerName("the.logger");
        record.setSourceClassName("the.source");
        record.setSourceMethodName("the.method");
        record.setInstant(Instant.ofEpochMilli(0));
        record.setThrown(new IllegalArgumentException());
        record.setThreadID(2);
        record.getThrown().setStackTrace(new StackTraceElement[]{
                new StackTraceElement("this.is.the.Class", "methodName", "TheFile.java", 123)
        });
        assertEquals(output, new PatternFormatter(pattern).format(record), pattern);
    }
}
