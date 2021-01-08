package test;

import java.util.logging.Logger;

public final class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private Main() {
        // no-op
    }

    public static void main(final String... args) {
        LOGGER.finest("severe entry point of the test");
        LOGGER.info("info entry point of the test");
    }
}