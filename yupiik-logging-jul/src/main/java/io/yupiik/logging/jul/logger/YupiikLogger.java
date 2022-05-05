/*
 * Copyright (c) 2021-2022 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.logging.jul.logger;

import io.yupiik.logging.jul.YupiikLoggerFactory;

import java.util.ResourceBundle;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

// a logger able to move from an actual logger to a delegating mode to reload an up to date logger
public class YupiikLogger extends Logger {
    private static final boolean GRAAL = isGraal();

    private static boolean isGraal() {
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) { // avoid reflection when not needed
            return true;
        }
        try {
            final var imageInfo = Class.forName("org.graalvm.nativeimage.ImageInfo");
            return Boolean.TRUE.equals(imageInfo.getMethod("inImageCode").invoke(null));
        } catch (final ReflectiveOperationException | IllegalArgumentException e) {
            return false;
        }
    }

    private volatile Logger delegate;
    private final ResourceBundle bundle;

    public YupiikLogger(final String name, final String resourceBundleName, final ResourceBundle bundle) {
        super(name, bundle == null ? resourceBundleName : null);
        this.bundle = bundle == null ? super.getResourceBundle() : bundle;
    }

    @Override
    public ResourceBundle getResourceBundle() {
        return bundle;
    }

    private Logger getDelegate() {
        if (delegate == null) {
            synchronized (this) {
                if (delegate == null) {
                    if (GRAAL) {
                        delegate = YupiikLoggerFactory.get().getLogger(super.getName(), super.getResourceBundleName());
                    } else {
                        delegate = this;
                    }
                }
            }
        }
        return delegate;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '[' + getName() + ']';
    }

    @Override
    public void log(final LogRecord record) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.log(record);
            return;
        }
        delegate.log(record);
    }

    @Override
    public void log(final Level level, final String msg) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.log(level, msg);
            return;
        }
        delegate.log(level, msg);
    }

    @Override
    public void log(final Level level, final Supplier<String> msgSupplier) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.log(level, msgSupplier);
            return;
        }
        delegate.log(level, msgSupplier);
    }

    @Override
    public void log(final Level level, final String msg, final Object param1) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.log(level, msg, param1);
            return;
        }
        delegate.log(level, msg, param1);
    }

    @Override
    public void log(final Level level, final String msg, final Object[] params) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.log(level, msg, params);
            return;
        }
        delegate.log(level, msg, params);
    }

    @Override
    public void log(final Level level, final String msg, final Throwable thrown) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.log(level, msg, thrown);
            return;
        }
        delegate.log(level, msg, thrown);
    }

    @Override
    public void log(final Level level, final Throwable thrown, final Supplier<String> msgSupplier) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.log(level, thrown, msgSupplier);
            return;
        }
        delegate.log(level, thrown, msgSupplier);
    }

    @Override
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logp(level, sourceClass, sourceMethod, msg);
            return;
        }
        delegate.logp(level, sourceClass, sourceMethod, msg);
    }

    @Override
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final Supplier<String> msgSupplier) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logp(level, sourceClass, sourceMethod, msgSupplier);
            return;
        }
        delegate.logp(level, sourceClass, sourceMethod, msgSupplier);
    }

    @Override
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg, final Object param1) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logp(level, sourceClass, sourceMethod, msg, param1);
            return;
        }
        delegate.logp(level, sourceClass, sourceMethod, msg, param1);
    }

    @Override
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg, final Object[] params) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logp(level, sourceClass, sourceMethod, msg, params);
            return;
        }
        delegate.logp(level, sourceClass, sourceMethod, msg, params);
    }

    @Override
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final String msg, final Throwable thrown) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logp(level, sourceClass, sourceMethod, msg, thrown);
            return;
        }
        delegate.logp(level, sourceClass, sourceMethod, msg, thrown);
    }

    @Override
    public void logp(final Level level, final String sourceClass, final String sourceMethod, final Throwable thrown, final Supplier<String> msgSupplier) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logp(level, sourceClass, sourceMethod, thrown, msgSupplier);
            return;
        }
        delegate.logp(level, sourceClass, sourceMethod, thrown, msgSupplier);
    }

    @Override
    @Deprecated
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName, final String msg) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logrb(level, sourceClass, sourceMethod, bundleName, msg);
            return;
        }
        delegate.logrb(level, sourceClass, sourceMethod, bundleName, msg);
    }

    @Override
    @Deprecated
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName, final String msg, final Object param1) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logrb(level, sourceClass, sourceMethod, bundleName, msg, param1);
            return;
        }
        delegate.logrb(level, sourceClass, sourceMethod, bundleName, msg, param1);
    }

    @Override
    @Deprecated
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName, final String msg, final Object[] params) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logrb(level, sourceClass, sourceMethod, bundleName, msg, params);
            return;
        }
        delegate.logrb(level, sourceClass, sourceMethod, bundleName, msg, params);
    }

    @Override
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, ResourceBundle bundle, final String msg, final Object... params) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logrb(level, sourceClass, sourceMethod, bundle, msg, params);
            return;
        }
        delegate.logrb(level, sourceClass, sourceMethod, bundle, msg, params);
    }

    @Override
    public void logrb(final Level level, ResourceBundle bundle, final String msg, final Object... params) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logrb(level, bundle, msg, params);
            return;
        }
        delegate.logrb(level, bundle, msg, params);
    }

    @Override
    @Deprecated
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, final String bundleName, final String msg, final Throwable thrown) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logrb(level, sourceClass, sourceMethod, bundleName, msg, thrown);
            return;
        }
        delegate.logrb(level, sourceClass, sourceMethod, bundleName, msg, thrown);
    }

    @Override
    public void logrb(final Level level, final String sourceClass, final String sourceMethod, ResourceBundle bundle, final String msg, final Throwable thrown) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logrb(level, sourceClass, sourceMethod, bundle, msg, thrown);
            return;
        }
        delegate.logrb(level, sourceClass, sourceMethod, bundle, msg, thrown);
    }

    @Override
    public void logrb(final Level level, ResourceBundle bundle, final String msg, final Throwable thrown) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.logrb(level, bundle, msg, thrown);
            return;
        }
        delegate.logrb(level, bundle, msg, thrown);
    }

    @Override
    public void entering(final String sourceClass, final String sourceMethod) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.entering(sourceClass, sourceMethod);
            return;
        }
        delegate.entering(sourceClass, sourceMethod);
    }

    @Override
    public void entering(final String sourceClass, final String sourceMethod, final Object param1) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.entering(sourceClass, sourceMethod, param1);
            return;
        }
        delegate.entering(sourceClass, sourceMethod, param1);
    }

    @Override
    public void entering(final String sourceClass, final String sourceMethod, final Object[] params) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.entering(sourceClass, sourceMethod, params);
            return;
        }
        delegate.entering(sourceClass, sourceMethod, params);
    }

    @Override
    public void exiting(final String sourceClass, final String sourceMethod) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.exiting(sourceClass, sourceMethod);
            return;
        }
        delegate.exiting(sourceClass, sourceMethod);
    }

    @Override
    public void exiting(final String sourceClass, final String sourceMethod, final Object result) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.exiting(sourceClass, sourceMethod, result);
            return;
        }
        delegate.exiting(sourceClass, sourceMethod, result);
    }

    @Override
    public void throwing(final String sourceClass, final String sourceMethod, final Throwable thrown) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.throwing(sourceClass, sourceMethod, thrown);
            return;
        }
        delegate.throwing(sourceClass, sourceMethod, thrown);
    }

    @Override
    public void severe(final String msg) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.severe(msg);
            return;
        }
        delegate.severe(msg);
    }

    @Override
    public void warning(final String msg) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.warning(msg);
            return;
        }
        delegate.warning(msg);
    }

    @Override
    public void info(final String msg) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.info(msg);
            return;
        }
        delegate.info(msg);
    }

    @Override
    public void config(final String msg) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.config(msg);
            return;
        }
        delegate.config(msg);
    }

    @Override
    public void fine(final String msg) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.fine(msg);
            return;
        }
        delegate.fine(msg);
    }

    @Override
    public void finer(final String msg) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.finer(msg);
            return;
        }
        delegate.finer(msg);
    }

    @Override
    public void finest(final String msg) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.finest(msg);
            return;
        }
        delegate.finest(msg);
    }

    @Override
    public void severe(final Supplier<String> msgSupplier) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.severe(msgSupplier);
            return;
        }
        delegate.severe(msgSupplier);
    }

    @Override
    public void warning(final Supplier<String> msgSupplier) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.warning(msgSupplier);
            return;
        }
        delegate.warning(msgSupplier);
    }

    @Override
    public void info(final Supplier<String> msgSupplier) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.info(msgSupplier);
            return;
        }
        delegate.info(msgSupplier);
    }

    @Override
    public void config(final Supplier<String> msgSupplier) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.config(msgSupplier);
            return;
        }
        delegate.config(msgSupplier);
    }

    @Override
    public void fine(final Supplier<String> msgSupplier) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.fine(msgSupplier);
            return;
        }
        delegate.fine(msgSupplier);
    }

    @Override
    public void finer(final Supplier<String> msgSupplier) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.finer(msgSupplier);
            return;
        }
        delegate.finer(msgSupplier);
    }

    @Override
    public void finest(final Supplier<String> msgSupplier) {
        final var delegate = getDelegate();
        if (delegate == this) {
            super.finest(msgSupplier);
            return;
        }
        delegate.finest(msgSupplier);
    }

    @Override
    public boolean isLoggable(final Level level) {
        final var delegate = getDelegate();
        if (delegate == this) {
            return super.isLoggable(level);
        }
        return delegate.isLoggable(level);
    }
}
