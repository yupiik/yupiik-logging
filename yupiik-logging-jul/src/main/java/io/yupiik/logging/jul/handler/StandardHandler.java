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
