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
package io.yupiik.logging.jul.formatter;

// inspired from tomcat JSONFilter but enforcing quoting of values (can need revisit but got proven fast enough)
final class JsonStrings {
    private JsonStrings() {
        // no-op
    }

    static StringBuilder escape(final String value) {
        final var sb = new StringBuilder(value.length() + 20);
        sb.append('"');

        StringBuilder escaped = null;
        int lastUnescapedStart = 0;
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c < 0x20 || c == 0x22 || c == 0x5c || Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
                if (escaped == null) {
                    escaped = new StringBuilder(value.length() + 20);
                }
                if (lastUnescapedStart < i) {
                    escaped.append(value.subSequence(lastUnescapedStart, i));
                }
                lastUnescapedStart = i + 1;
                final char popular = getPopularChar(c);
                if (popular > 0) {
                    escaped.append('\\').append(popular);
                } else {
                    escaped.append("\\u");

                    final var hex = "000" + Integer.toHexString(c);
                    escaped.append(hex.substring(hex.length() - 4));
                }
            }
        }
        if (escaped == null) {
            sb.append(value);
        } else {
            if (lastUnescapedStart < value.length()) {
                escaped.append(value.subSequence(lastUnescapedStart, value.length()));
            }
            sb.append(escaped);
        }

        sb.append('"');
        return sb;
    }

    private static char getPopularChar(char c) {
        switch (c) {
            case '"':
            case '\\':
            case '/':
                return c;
            case 0x8:
                return 'b';
            case 0xc:
                return 'f';
            case 0xa:
                return 'n';
            case 0xd:
                return 'r';
            case 0x9:
                return 't';
            default:
                return 0;
        }
    }
}
