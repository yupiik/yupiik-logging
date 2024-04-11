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
package io.yupiik.logging.jul;

public final class YupiikLoggerFactory {
    private static volatile YupiikLoggers delegate;

    private YupiikLoggerFactory() {
        // no-op
    }

    public static YupiikLoggers get() {
        if (delegate == null) {
            synchronized (YupiikLoggers.class) {
                if (delegate == null) {
                    delegate = new YupiikLoggers();
                }
            }
        }
        return delegate;
    }

    // must be called only in a safe env
    public static void set(final YupiikLoggers loggers) {
        if (delegate != null) {
            delegate.close();
        }
        delegate = loggers;
    }
}
