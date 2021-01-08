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
