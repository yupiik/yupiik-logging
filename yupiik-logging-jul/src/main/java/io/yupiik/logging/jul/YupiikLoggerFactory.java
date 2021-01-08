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
