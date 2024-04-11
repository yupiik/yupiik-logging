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

final class JsonStrings {
    private JsonStrings() {
        // no-op
    }

    static String escape(final String value) {
        final var sb = new StringBuilder(value.length());
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (isPassthrough(c)) {
                sb.append(c);
                continue;
            }
            sb.append(escape(c));
        }
        sb.append('"');
        return sb.toString();
    }

    public static String escape(final char c) {
        if (isPassthrough(c)) {
            return String.valueOf(c);
        }
        switch (c) {
            case '"':
            case '\\':
                return "\\" + c;
            case '\b':
                return "\\b";
            case '\f':
                return "\\f";
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\t':
                return "\\t";
            default:
                final var hex = "000" + Integer.toHexString(c);
                return "\\u" + hex.substring(hex.length() - 4);
        }
    }

    private static boolean isPassthrough(final char c) {
        return c >= 0x20 && c != 0x22 && c != 0x5c;
    }
}
