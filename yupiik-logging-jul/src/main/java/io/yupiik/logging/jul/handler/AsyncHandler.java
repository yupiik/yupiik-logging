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
package io.yupiik.logging.jul.handler;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

// mainly an async handler for stream contentions
// so we format in the caller thread to distribute the load and only append in a single thread
public class AsyncHandler extends Handler {
    private final Handler delegate;
    private final BlockingQueue<LogRecord> queue;
    private final Worker[] workers;
    private final Integer queueSize;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public AsyncHandler() {
        final var className = AsyncHandler.class.getName();
        final var logManager = getPropertySupplier();

        final var delegateClass = logManager.apply(className + ".delegate.class");
        if (delegateClass == null) {
            delegate = new StandardHandler();
        } else {
            try {
                delegate = AsyncHandler.class.getClassLoader().loadClass(delegateClass)
                        .asSubclass(Handler.class)
                        .getConstructor()
                        .newInstance();
            } catch (final Exception e) {
                reportError(e.getMessage(), e, ErrorManager.FORMAT_FAILURE);
                throw new IllegalStateException(e);
            }
        }
        delegate.setErrorManager(ofNullable(getErrorManager()).orElseGet(ErrorManager::new));

        final var lvl = logManager.apply(className + ".level");
        if (lvl != null) {
            delegate.setLevel(Level.parse(lvl));
        }

        final var filter = logManager.apply(className + ".filter");
        if (filter != null) {
            try {
                delegate.setFilter(AsyncHandler.class.getClassLoader().loadClass(filter).asSubclass(Filter.class).getConstructor().newInstance());
            } catch (final Exception e) {
                reportError(e.getMessage(), e, ErrorManager.FORMAT_FAILURE);
            }
        }

        final var formatter = logManager.apply(className + ".formatter");
        if (formatter != null) {
            try {
                delegate.setFormatter(AsyncHandler.class.getClassLoader().loadClass(formatter).asSubclass(Formatter.class).getConstructor().newInstance());
            } catch (final Exception e) {
                reportError(e.getMessage(), e, ErrorManager.FORMAT_FAILURE);
            }
        }
        final var encoding = logManager.apply(className + ".encoding");
        try {
            if (encoding != null) {
                delegate.setEncoding(encoding);
            } else {
                delegate.setEncoding(getEncoding());
            }
        } catch (final Exception e) {
            reportError(e.getMessage(), e, ErrorManager.FORMAT_FAILURE);
        }

        queueSize = ofNullable(logManager.apply(className + ".queue.size"))
                .map(Integer::parseInt)
                .orElse(512);
        this.queue = new ArrayBlockingQueue<>(queueSize);

        final var workerCount = ofNullable(logManager.apply(className + ".worker.count"))
                .map(Integer::parseInt)
                .orElse(1);
        workers = IntStream.range(0, workerCount)
                .mapToObj(i -> new Worker(i, AsyncHandler.this, running))
                .toArray(Worker[]::new);
    }

    @Override
    public void setFormatter(final Formatter newFormatter) throws SecurityException {
        delegate.setFormatter(newFormatter);
    }

    @Override
    public void setEncoding(final String encoding) throws SecurityException, UnsupportedEncodingException {
        delegate.setEncoding(encoding);
    }

    @Override
    public void setFilter(final Filter newFilter) throws SecurityException {
        delegate.setFilter(newFilter);
    }

    @Override
    public void setErrorManager(final ErrorManager em) {
        delegate.setErrorManager(em);
    }

    @Override
    public void setLevel(final Level newLevel) throws SecurityException {
        delegate.setLevel(newLevel);
    }

    @Override
    public void publish(final LogRecord record) {
        if (isLoggable(record)) {
            // infer in context if needed
            record.getSourceClassName();
            record.getSourceMethodName();
            queue.add(record);
        }
    }

    @Override
    public void flush() {
        doFlush(queueSize);
    }

    @Override
    public void close() throws SecurityException {
        running.set(false);
        Stream.of(workers).forEach(w -> {
            try {
                w.join(MINUTES.toMillis(1));
            } catch (final InterruptedException e) {
                // no-op
            }
        });
        doFlush(Integer.MAX_VALUE);
        delegate.close();
    }

    private void doFlush(final int max) {
        int remaining = max;
        LogRecord next;
        while ((next = queue.poll()) != null && remaining-- > 0) {
            delegate.publish(next);
        }
    }

    protected Function<String, String> getPropertySupplier() {
        return LogManager.getLogManager()::getProperty;
    }

    private static class Worker extends Thread {
        public Worker(final int index, final AsyncHandler root, final AtomicBoolean running) {
            super(() -> {
                LogRecord next;
                while (running.get()) {
                    try {
                        next = root.queue.poll(250, MILLISECONDS);
                        if (next != null) {
                            root.delegate.publish(next);
                        }
                    } catch (final RuntimeException re) {
                        root.getErrorManager().error(re.getMessage(), re, ErrorManager.FORMAT_FAILURE);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, AsyncHandler.class.getName() + "-" + (index + 1));
            setDaemon(true);
            start();
        }
    }
}
