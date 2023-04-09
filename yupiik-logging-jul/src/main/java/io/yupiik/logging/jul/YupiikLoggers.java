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
package io.yupiik.logging.jul;

import io.yupiik.logging.jul.formatter.InlineFormatter;
import io.yupiik.logging.jul.formatter.JsonFormatter;
import io.yupiik.logging.jul.formatter.PatternFormatter;
import io.yupiik.logging.jul.handler.LocalFileHandler;
import io.yupiik.logging.jul.handler.StandardHandler;
import io.yupiik.logging.jul.handler.StdoutHandler;
import io.yupiik.logging.jul.logger.YupiikLogger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Collections.enumeration;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

// actual LogManager but detached from JUL inheritance, however it should look like JUL in terms of config
//
// note: it is not a 100% complete impl yet, it mainly targets docker containers for now and some features as config listeners are ignored
public class YupiikLoggers {
    private final Pattern posix = Pattern.compile("[^A-Za-z0-9]");

    public static class State { // makes it easy to reset at once
        private final ConcurrentMap<Runnable, Runnable> listeners = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, YupiikLogger> loggers = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, String> configuration = new ConcurrentHashMap<>();
        private final AtomicBoolean configurationRead = new AtomicBoolean(false);
        private volatile Thread shutdownHook;

        private State() {
            // no-op
        }
    }

    private State state = new State();

    public synchronized void close() {
        if (state.shutdownHook != null) {
            state.shutdownHook.run();
            state.shutdownHook = null;
        }
    }

    public boolean addLogger(final Logger logger) {
        if (!YupiikLogger.class.isInstance(logger)) {
            state.loggers.putIfAbsent(logger.getName(), createLogger(logger.getName(), logger.getResourceBundleName(), logger.getResourceBundle()));
            return false;
        }
        return state.loggers.putIfAbsent(logger.getName(), YupiikLogger.class.cast(logger)) == null;
    }

    public Logger getLogger(final String name, final String bundle) {
        final var logger = getLoggerOrNull(name);
        if (logger != null) {
            return logger;
        }
        final var newInstance = createLogger(name, bundle, null);
        final var existing = state.loggers.putIfAbsent(name, newInstance);
        if (existing != null) {
            return existing;
        }
        return newInstance;
    }

    public YupiikLogger getLoggerOrNull(final String name) {
        return state.loggers.get(name);
    }

    public String getProperty(final String name) {
        return ofNullable(System.getProperty(name))
                .or(() -> ofNullable(System.getenv(posix.matcher(name).replaceAll("_").toUpperCase(ROOT))))
                .orElseGet(() -> state.configuration.get(name));
    }

    public Enumeration<String> getLoggerNames() {
        return enumeration(state.loggers.keySet());
    }

    // todo: should we just reset the handlers?
    public void reset() throws SecurityException {
        state = new State();
    }

    public synchronized void readConfiguration() throws IOException, SecurityException {
        if (!state.configurationRead.compareAndSet(false, true)) {
            return;
        }
        final var hook = new Thread(() -> state.loggers.values().stream()
                .flatMap(it -> Stream.of(it.getHandlers()))
                .distinct()
                .forEach(it -> {
                    try {
                        it.close();
                    } catch (final Exception e) {
                        e.printStackTrace();
                    }
                }), getClass().getName() + "-shutdown");
        state.shutdownHook = hook;
        Runtime.getRuntime().addShutdownHook(hook);
        final var location = getProperty("java.util.logging.config.file");
        if (location != null) {
            final var path = Paths.get(location);
            if (Files.exists(path)) {
                readConfiguration(Files.newInputStream(path));
                return;
            }
            final InputStream resource = Thread.currentThread().getContextClassLoader().getResourceAsStream(location);
            if (resource != null) {
                readConfiguration(resource);
                return;
            }
        }
        readConfiguration(new ByteArrayInputStream(("" +
                ".level=INFO\n" +
                ".handlers=io.yupiik.logging.jul.handler.StandardHandler\n" +
                "").getBytes(StandardCharsets.UTF_8)));
    }

    public void readConfiguration(final InputStream inputStream) throws IOException, SecurityException {
        final Properties properties = new Properties();
        try (final InputStream stream = inputStream) {
            properties.load(stream);
        }
        final var newConfig = properties.stringPropertyNames().stream()
                .collect(toMap(identity(), properties::getProperty));
        if (!newConfig.equals(state.configuration)) {
            state.configuration.clear();
            state.configuration.putAll(newConfig);
        }
        invokeListeners();
    }

    public void updateConfiguration(final Function<String, BiFunction<String, String, String>> mapper) {
        for (final Map.Entry<String, String> entry : new ArrayList<>(state.configuration.entrySet())) {
            final var kvMapper = mapper.apply(entry.getKey());
            final var newValue = kvMapper.apply(null, entry.getValue()); // assume previous == missing == null
            if (newValue == null) {
                state.configuration.remove(entry.getKey());
            } else {
                state.configuration.put(entry.getKey(), newValue);
            }
        }
        invokeListeners();
    }

    private void invokeListeners() {
        if (state.listeners.isEmpty()) {
            return;
        }
        Throwable firstError = null;
        for (final Runnable c : state.listeners.values()) { // mimic JVM behavior there
            try {
                c.run();
            } catch (final ThreadDeath death) {
                throw death;
            } catch (final Error | RuntimeException x) {
                if (firstError == null) {
                    firstError = x;
                } else {
                    firstError.addSuppressed(x);
                }
            }
        }
        if (Error.class.isInstance(firstError)) {
            throw Error.class.cast(firstError);
        }
        if (RuntimeException.class.isInstance(firstError)) {
            throw RuntimeException.class.cast(firstError);
        }
    }

    public void addConfigurationListener(final Runnable listener) {
        state.listeners.put(listener, listener);
    }

    public void removeConfigurationListener(final Runnable listener) {
        state.listeners.remove(listener);
    }

    private YupiikLogger createLogger(final String name, final String bundle, final ResourceBundle resourceBundle) {
        try { // will test if already read so fine to call each time
            readConfiguration();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        final var logger = new YupiikLogger(name, bundle, resourceBundle);
        configure(logger);

        // now create parent tree
        Logger current = logger;
        while (current.getParent() == null) {
            int dot = current.getName().lastIndexOf('.');
            if (dot < 0) {
                break;
            }
            final var parent = getLogger(current.getName().substring(0, dot), null);
            logger.setParent(parent);
            current = parent;
        }
        if (!name.isEmpty() && current.getParent() == null) {
            logger.setParent(getLogger("", null));
        }
        return logger;
    }

    private void configure(final YupiikLogger logger) {
        final var level = getPropertyOrParentValue(logger.getName(), ".level");
        // JUL does not always do that but it is common to set the level for subloggers too at the same time
        logger.setLevel(level == null ? Level.INFO : Level.parse(level));

        final var useParents = getProperty(logger.getName() + ".useParentHandlers");
        logger.setUseParentHandlers(useParents == null || Boolean.parseBoolean(useParents));

        final var filter = getProperty(logger.getName() + ".filter");
        if (filter != null) {
            try {
                logger.setFilter(Thread.currentThread().getContextClassLoader()
                        .loadClass(filter.trim())
                        .asSubclass(Filter.class)
                        .getConstructor()
                        .newInstance());
            } catch (final InstantiationException | IllegalAccessException | InvocationTargetException |
                           NoSuchMethodException | ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }

        final var handlers = getProperty(logger.getName() + ".handlers");
        if (handlers != null) {
            if (logger.getHandlers().length > 0) { // reset case
                Stream.of(logger.getHandlers()).forEach(logger::removeHandler);
            }
            Stream.of(handlers.split(","))
                    .map(String::trim)
                    .filter(it -> !it.isEmpty())
                    .map(this::createHandler)
                    .forEach(logger::addHandler);
        }
    }

    private String getPropertyOrParentValue(final String name, final String sub) {
        String current = name;
        do {
            final var value = getProperty(current + sub);
            if (value != null) {
                return value;
            }
            final var sep = current.lastIndexOf('.');
            if (sep > 0) {
                current = current.substring(0, sep);
            } else {
                current = "";
            }
        } while (!current.isEmpty());
        return getProperty(sub);
    }

    @SuppressWarnings("unchecked")
    private Handler createHandler(final String handlerType) {
        final var handler = newHandler(handlerType);

        final var formatter = getProperty(handlerType + ".formatter");
        if (formatter != null) {
            switch (formatter.trim().toLowerCase(ROOT)) {
                case "pattern":
                    handler.setFormatter(new PatternFormatter(getProperty(PatternFormatter.class.getName() + ".pattern")));
                    break;
                case "inline":
                    handler.setFormatter(new InlineFormatter());
                    break;
                case "json":
                    handler.setFormatter(new JsonFormatter());
                    break;
                default:
                    if (formatter.startsWith("pattern(") && formatter.endsWith(")")) {
                        handler.setFormatter(new PatternFormatter(formatter.substring("pattern(".length(), formatter.length() - 1)));
                        break;
                    }
                    if (formatter.startsWith("json(") && formatter.endsWith(")")) {
                        final var conf = formatter.substring("json(".length(), formatter.length() - 1);
                        final var config = Stream.of(conf.split(";"))
                                .map(it -> it.split("="))
                                .collect(toMap(it -> it[0], it -> it[1]));
                        final var jsonFormatter = new JsonFormatter();
                        jsonFormatter.setUseUUID(Boolean.parseBoolean(config.get("useUUID")));
                        jsonFormatter.setFormatMessage(Boolean.parseBoolean(config.get("formatMessage")));
                        ofNullable(config.get("customEntriesMapper")).ifPresent(clazz -> {
                            try {
                                jsonFormatter.setCustomEntriesMapper(Thread.currentThread().getContextClassLoader()
                                        .loadClass(clazz.trim())
                                        .asSubclass(Function.class)
                                        .getConstructor()
                                        .newInstance());
                            } catch (final InstantiationException | IllegalAccessException | InvocationTargetException |
                                           NoSuchMethodException | ClassNotFoundException e) {
                                throw new IllegalArgumentException(e);
                            }
                        });
                        handler.setFormatter(jsonFormatter);
                        break;
                    }
                    try {
                        handler.setFormatter(Thread.currentThread().getContextClassLoader()
                                .loadClass(formatter.trim())
                                .asSubclass(Formatter.class)
                                .getConstructor()
                                .newInstance());
                    } catch (final InstantiationException | IllegalAccessException | InvocationTargetException |
                                   NoSuchMethodException | ClassNotFoundException e) {
                        throw new IllegalArgumentException(e);
                    }
            }
        } else {
            handler.setFormatter(new InlineFormatter());
        }

        final var encoding = getProperty(handlerType + ".encoding");
        if (encoding != null) {
            try {
                handler.setEncoding(encoding);
            } catch (UnsupportedEncodingException e) {
                try {
                    handler.setEncoding(StandardCharsets.UTF_8.name());
                } catch (final UnsupportedEncodingException unsupportedEncodingException) {
                    // no-op
                }
            }
        }

        final var level = getProperty(handlerType + ".level");
        if (level != null) {
            handler.setLevel(Level.parse(level));
        }

        return handler;
    }

    private Handler newHandler(String it) {
        switch (it) {
            case "std":
            case "standard":
            case "io.yupiik.logging.jul.handler.StandardHandler":
                return new StandardHandler();
            case "stdout":
            case "io.yupiik.logging.jul.handler.StdoutHandler":
                return new StdoutHandler();
            case "file":
            case "local":
            case "io.yupiik.logging.jul.handler.LocalFileHandler":
                return new LocalFileHandler() {
                    @Override
                    protected <T> T getProperty(final String name, final Function<String, T> mapper, final Supplier<T> defaultValue) {
                        return ofNullable(YupiikLoggers.this.getProperty(name)).map(mapper).orElseGet(defaultValue);
                    }
                };
            default:
                try {
                    return Thread.currentThread().getContextClassLoader()
                            .loadClass(it.trim())
                            .asSubclass(Handler.class)
                            .getConstructor()
                            .newInstance();
                } catch (final InstantiationException | IllegalAccessException | InvocationTargetException |
                               NoSuchMethodException | ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
        }
    }
}
