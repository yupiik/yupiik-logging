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
package io.yupiik.logging.jul.handler;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class StandardHandler extends Handler {
    @Override
    public void publish(final LogRecord record) {
        if (isLoggable(record)) {
            publish(record, getStream(record));
        }
    }

    @Override
    public void flush() {
        // no-op
    }

    @Override
    public void close() throws SecurityException {
        flush();
    }

    protected PrintStream getStream(final LogRecord record) {
        return record.getLevel().intValue() > Level.INFO.intValue() ? System.err : System.out;
    }

    private void publish(final LogRecord record, final PrintStream out) {
        try {
            out.write(getFormatter().format(record).getBytes(StandardCharsets.UTF_8));
        } catch (final Exception ex) {
            ex.printStackTrace(System.err);
        }
    }
}
