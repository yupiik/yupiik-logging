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
package io.yupiik.logging.jul;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class YupiikLogManager extends LogManager {
    @Override
    public boolean addLogger(final Logger logger) {
        return YupiikLoggerFactory.get().addLogger(logger);
    }

    @Override
    public Logger getLogger(final String name) {
        return YupiikLoggerFactory.get().getLogger(name, null);
    }

    @Override
    public String getProperty(final String name) {
        return YupiikLoggerFactory.get().getProperty(name);
    }

    @Override
    public Enumeration<String> getLoggerNames() {
        return YupiikLoggerFactory.get().getLoggerNames();
    }

    @Override
    public void readConfiguration() throws IOException, SecurityException {
        YupiikLoggerFactory.get().readConfiguration();
    }

    @Override
    public void reset() throws SecurityException {
        YupiikLoggerFactory.get().reset();
    }

    @Override
    public void readConfiguration(final InputStream ins) throws IOException, SecurityException {
        YupiikLoggerFactory.get().readConfiguration(ins);
    }

    @Override
    public void updateConfiguration(final Function<String, BiFunction<String, String, String>> mapper) throws IOException {
        YupiikLoggerFactory.get().updateConfiguration(mapper);
    }

    @Override
    public void updateConfiguration(final InputStream ins, final Function<String, BiFunction<String, String, String>> mapper) throws IOException {
        final var loggers = YupiikLoggerFactory.get();
        loggers.readConfiguration(ins);
        loggers.updateConfiguration(mapper);
    }

    @Override
    public void checkAccess() throws SecurityException {
        // no-op
    }

    @Override
    public LogManager addConfigurationListener(final Runnable listener) {
        YupiikLoggerFactory.get().addConfigurationListener(listener);
        return this;
    }

    @Override
    public void removeConfigurationListener(final Runnable listener) {
        YupiikLoggerFactory.get().removeConfigurationListener(listener);
    }
}
