package com.divinity.util;

import java.io.IOException;
import java.util.logging.*;

public final class Logger {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger("divinity");

    static {
        LOG.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return String.format("[%s] %s%n", record.getLevel().getLocalizedName(), record.getMessage());
            }
        });
        LOG.addHandler(handler);
        LOG.setLevel(Level.INFO);
    }

    public static void setVerbose(boolean verbose) {
        LOG.setLevel(verbose ? Level.FINE : Level.INFO);
    }

    public static void info(String msg, Object... args) {
        LOG.info(String.format(msg, args));
    }

    public static void fine(String msg, Object... args) {
        LOG.fine(String.format(msg, args));
    }

    public static void warn(String msg, Object... args) {
        LOG.warning(String.format(msg, args));
    }

    public static void error(String msg, Object... args) {
        LOG.severe(String.format(msg, args));
    }

    public static void error(String msg, Throwable t, Object... args) {
        LOG.log(Level.SEVERE, String.format(msg, args), t);
    }
}
