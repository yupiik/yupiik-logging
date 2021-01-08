package io.yupiik.logging.jul.handler;

import java.io.PrintStream;
import java.util.logging.LogRecord;

public class StdoutHandler extends StandardHandler {
    @Override
    protected PrintStream getStream(final LogRecord record) {
        return System.out;
    }
}
