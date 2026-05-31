package com.divinity.cli;

import java.io.PrintStream;
import java.util.*;

public final class ProgressBar {

    private final PrintStream out;
    private final int total;
    private final int width;
    private int current;
    private long startTime;
    private String currentTask;

    public ProgressBar(int total, int width) {
        this(System.out, total, width);
    }

    public ProgressBar(PrintStream out, int total, int width) {
        this.out = out;
        this.total = total;
        this.width = width;
        this.current = 0;
        this.startTime = System.currentTimeMillis();
        this.currentTask = "";
    }

    public void start() {
        startTime = System.currentTimeMillis();
        render();
    }

    public void update(int progress) {
        this.current = progress;
        render();
    }

    public void update(int progress, String task) {
        this.current = progress;
        this.currentTask = task;
        render();
    }

    public void increment() {
        update(current + 1);
    }

    public void increment(String task) {
        update(current + 1, task);
    }

    public void finish() {
        current = total;
        render();
        out.println();
    }

    private void render() {
        double percentage = total > 0 ? (double) current / total : 0;
        int filled = (int) (width * percentage);

        long elapsed = System.currentTimeMillis() - startTime;
        long eta = current > 0 ? (elapsed * (total - current)) / current : 0;

        double speed = elapsed > 0 ? (current * 1000.0) / elapsed : 0;

        StringBuilder bar = new StringBuilder();
        bar.append("\r");
        bar.append(Colors.CYAN).append("[");

        for (int i = 0; i < width; i++) {
            if (i < filled) {
                bar.append(Colors.GREEN).append("█");
            } else if (i == filled) {
                bar.append(Colors.YELLOW).append("▓");
            } else {
                bar.append(Colors.DARK_GRAY).append("░");
            }
        }

        bar.append(Colors.CYAN).append("]").append(Colors.RESET);

        bar.append(String.format(" %s%d/%d%s",
            Colors.WHITE, current, total, Colors.RESET));

        bar.append(String.format(" %s%.1f%%%s",
            Colors.BRIGHT_CYAN, percentage * 100, Colors.RESET));

        bar.append(String.format(" %s%.1f cls/s%s",
            Colors.BRIGHT_GREEN, speed, Colors.RESET));

        if (eta > 0) {
            bar.append(String.format(" %sETA: %s%s",
                Colors.BRIGHT_YELLOW, formatTime(eta), Colors.RESET));
        }

        if (!currentTask.isEmpty()) {
            bar.append(String.format(" %s%s%s",
                Colors.DARK_GRAY, truncate(currentTask, 30), Colors.RESET));
        }

        out.print(bar.toString());
        out.flush();
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) return minutes + "m " + seconds + "s";
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m";
    }

    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    public static final class Colors {
        public static final String RESET = "[0m";
        public static final String BLACK = "[30m";
        public static final String RED = "[31m";
        public static final String GREEN = "[32m";
        public static final String YELLOW = "[33m";
        public static final String BLUE = "[34m";
        public static final String MAGENTA = "[35m";
        public static final String CYAN = "[36m";
        public static final String WHITE = "[37m";
        public static final String DARK_GRAY = "[90m";
        public static final String BRIGHT_RED = "[91m";
        public static final String BRIGHT_GREEN = "[92m";
        public static final String BRIGHT_YELLOW = "[93m";
        public static final String BRIGHT_BLUE = "[94m";
        public static final String BRIGHT_MAGENTA = "[95m";
        public static final String BRIGHT_CYAN = "[96m";
        public static final String BRIGHT_WHITE = "[97m";

        public static final String BOLD = "[1m";
        public static final String DIM = "[2m";
        public static final String ITALIC = "[3m";
        public static final String UNDERLINE = "[4m";

        private Colors() {}

        public static boolean isSupported() {
            String os = System.getProperty("os.name").toLowerCase();
            String term = System.getenv("TERM");
            return !os.contains("win") || term != null;
        }

        public static String strip(String text) {
            return text.replaceAll("\\[[;\\d]*m", "");
        }
    }
}
