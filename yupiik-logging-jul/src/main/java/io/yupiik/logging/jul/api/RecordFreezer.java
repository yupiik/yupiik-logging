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
package io.yupiik.logging.jul.api;

import java.time.Instant;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Enables to give a formatter the needed API to stack a context in async mode.
 * <p>
 * As of today it is only used in {@link io.yupiik.logging.jul.handler.AsyncHandler} and integrated with {@link io.yupiik.logging.jul.formatter.JsonFormatter}.
 */
public interface RecordFreezer extends Function<LogRecord, LogRecord> {
    // class to enable to enrich the log record with a context easily
    //
    // it has some impacts - generally ok - see https://bugs.openjdk.org/browse/JDK-6569068
    // note that for 100% JVM case using unsafe to bypass the constructor can be more efficient - but we target graal and future jvm
    // so need to stay away from it for now
    class FrozenLogRecord<T> extends LogRecord {
        private final LogRecord delegate;
        private final T data;
        private final String creator;

        /**
         * @param delegate the original record.
         * @param data the data to enrich the record with (mdc like).
         * @param creator a marker to identify the record you created if multiple sources wraps the record.
         */
        public FrozenLogRecord(final LogRecord delegate,
                               final T data, final String creator) {
            super(delegate.getLevel(), delegate.getLoggerName());
            this.delegate = delegate;
            this.data = data;
            this.creator = creator;
        }

        public String getCreator() {
            return creator;
        }

        public T getData() {
            return this.data;
        }

        public LogRecord getDelegate() {
            return delegate;
        }

        @Override
        public String getLoggerName() {
            return delegate.getLoggerName();
        }

        @Override
        public ResourceBundle getResourceBundle() {
            return delegate.getResourceBundle();
        }

        @Override
        public String getResourceBundleName() {
            return delegate.getResourceBundleName();
        }

        @Override
        public Level getLevel() {
            return delegate.getLevel();
        }

        @Override
        public long getSequenceNumber() {
            return delegate.getSequenceNumber();
        }

        @Override
        public String getSourceClassName() {
            return delegate.getSourceClassName();
        }

        @Override
        public String getSourceMethodName() {
            return delegate.getSourceMethodName();
        }

        @Override
        public String getMessage() {
            return delegate.getMessage();
        }

        @Override
        public Object[] getParameters() {
            return delegate.getParameters();
        }

        @Override
        public int getThreadID() {
            return delegate.getThreadID();
        }

        @Override
        public long getMillis() {
            return delegate.getMillis();
        }

        @Override
        public Instant getInstant() {
            return delegate.getInstant();
        }

        @Override
        public Throwable getThrown() {
            return delegate.getThrown();
        }

        /* @Override
        public long getLongThreadID() {
            return delegate.getLongThreadID();
        }
        */
    }

}
