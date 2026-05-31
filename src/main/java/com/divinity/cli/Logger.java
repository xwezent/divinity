package com.divinity.cli;

import java.io.PrintStream;
import java.util.*;

public final class Logger {

    private static final PrintStream out = System.out;
    private static final PrintStream err = System.err;
    private static LogLevel currentLevel = LogLevel.INFO;
    private static boolean useColors = ProgressBar.Colors.isSupported();

    public static void setLevel(LogLevel level) {
        currentLevel = level;
    }

    public static void setUseColors(boolean use) {
        useColors = use;
    }

    public static void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    public static void info(String message) {
        log(LogLevel.INFO, message);
    }

    public static void success(String message) {
        log(LogLevel.SUCCESS, message);
    }

    public static void warn(String message) {
        log(LogLevel.WARN, message);
    }

    public static void error(String message) {
        log(LogLevel.ERROR, message);
    }

    public static void error(String message, Throwable t) {
        log(LogLevel.ERROR, message);
        if (currentLevel.ordinal() <= LogLevel.DEBUG.ordinal()) {
            t.printStackTrace(err);
        }
    }

    private static void log(LogLevel level, String message) {
        if (level.ordinal() < currentLevel.ordinal()) return;

        String prefix = useColors ? level.coloredPrefix() : level.prefix();
        String msg = useColors ? message : ProgressBar.Colors.strip(message);

        PrintStream stream = level == LogLevel.ERROR ? err : out;
        stream.println(prefix + msg + (useColors ? ProgressBar.Colors.RESET : ""));
    }

    public static void banner(String text) {
        out.println();
        out.println(useColors ? ProgressBar.Colors.BRIGHT_CYAN + text + ProgressBar.Colors.RESET : text);
        out.println();
    }

    public static void section(String title) {
        out.println();
        String line = "═".repeat(Math.min(title.length() + 4, 80));
        if (useColors) {
            out.println(ProgressBar.Colors.CYAN + line + ProgressBar.Colors.RESET);
            out.println(ProgressBar.Colors.BRIGHT_WHITE + "  " + title + ProgressBar.Colors.RESET);
            out.println(ProgressBar.Colors.CYAN + line + ProgressBar.Colors.RESET);
        } else {
            out.println(line);
            out.println("  " + title);
            out.println(line);
        }
        out.println();
    }

    public static void table(String[][] data) {
        if (data.length == 0) return;

        int[] widths = new int[data[0].length];
        for (String[] row : data) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], ProgressBar.Colors.strip(row[i]).length());
            }
        }

        for (int i = 0; i < data.length; i++) {
            StringBuilder line = new StringBuilder();
            for (int j = 0; j < data[i].length; j++) {
                String cell = data[i][j];
                int padding = widths[j] - ProgressBar.Colors.strip(cell).length();
                line.append(cell).append(" ".repeat(padding + 2));
            }
            out.println(line.toString());

            if (i == 0) {
                StringBuilder separator = new StringBuilder();
                for (int w : widths) {
                    separator.append("─".repeat(w + 2));
                }
                out.println(useColors ?
                    ProgressBar.Colors.DARK_GRAY + separator + ProgressBar.Colors.RESET :
                    separator.toString());
            }
        }
        out.println();
    }

    public enum LogLevel {
        DEBUG("DEBUG", ProgressBar.Colors.DARK_GRAY),
        INFO("INFO", ProgressBar.Colors.BRIGHT_BLUE),
        SUCCESS("SUCCESS", ProgressBar.Colors.BRIGHT_GREEN),
        WARN("WARN", ProgressBar.Colors.BRIGHT_YELLOW),
        ERROR("ERROR", ProgressBar.Colors.BRIGHT_RED);

        private final String name;
        private final String color;

        LogLevel(String name, String color) {
            this.name = name;
            this.color = color;
        }

        public String prefix() {
            return "[" + name + "] ";
        }

        public String coloredPrefix() {
            return color + "[" + name + "]" + ProgressBar.Colors.RESET + " ";
        }
    }
}
