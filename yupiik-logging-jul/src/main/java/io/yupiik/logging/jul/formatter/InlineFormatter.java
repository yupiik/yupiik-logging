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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class InlineFormatter extends Formatter {
    @Override
    public String format(final LogRecord record) {
        return record.getInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) +
                " [" + record.getLevel().getName() + "][" + record.getLoggerName() + "] " +
                formatMessage(record) + toString(record.getThrown()) + '\n';
    }

    private String toString(final Throwable thrown) {
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
