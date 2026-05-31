package com.divinity.profiler;

import java.util.*;
import java.util.concurrent.atomic.*;

public final class DecompilationProfiler {

    private final Map<String, ProfileEntry> profiles;
    private final ThreadLocal<Deque<TimingContext>> contextStack;

    public DecompilationProfiler() {
        this.profiles = new HashMap<>();
        this.contextStack = ThreadLocal.withInitial(ArrayDeque::new);
    }

    public synchronized void startPhase(String phaseName) {
        Deque<TimingContext> stack = contextStack.get();
        stack.push(new TimingContext(phaseName, System.nanoTime()));
    }

    public synchronized void endPhase(String phaseName) {
        Deque<TimingContext> stack = contextStack.get();
        if (stack.isEmpty()) return;

        TimingContext ctx = stack.pop();
        if (!ctx.name.equals(phaseName)) {
            return;
        }

        long duration = System.nanoTime() - ctx.startTime;
        profiles.computeIfAbsent(phaseName, k -> new ProfileEntry(phaseName))
            .addSample(duration);
    }

    public synchronized ProfileReport generateReport() {
        Map<String, PhaseStats> stats = new LinkedHashMap<>();
        long totalTime = 0;

        for (ProfileEntry entry : profiles.values()) {
            long count = entry.count.get();
            long total = entry.totalTime.get();
            long min = entry.minTime.get();
            long max = entry.maxTime.get();
            long avg = count > 0 ? total / count : 0;

            PhaseStats phaseStats = new PhaseStats(entry.name, count, total, min, max, avg);
            stats.put(entry.name, phaseStats);
            totalTime += total;
        }

        return new ProfileReport(stats, totalTime);
    }

    public synchronized void reset() {
        profiles.clear();
        contextStack.get().clear();
    }

    private static class TimingContext {
        final String name;
        final long startTime;

        TimingContext(String name, long startTime) {
            this.name = name;
            this.startTime = startTime;
        }
    }

    private static class ProfileEntry {
        final String name;
        final AtomicLong count = new AtomicLong(0);
        final AtomicLong totalTime = new AtomicLong(0);
        final AtomicLong minTime = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong maxTime = new AtomicLong(0);

        ProfileEntry(String name) {
            this.name = name;
        }

        synchronized void addSample(long duration) {
            count.incrementAndGet();
            totalTime.addAndGet(duration);
            minTime.updateAndGet(current -> Math.min(current, duration));
            maxTime.updateAndGet(current -> Math.max(current, duration));
        }
    }

    public record PhaseStats(
        String name,
        long count,
        long totalNanos,
        long minNanos,
        long maxNanos,
        long avgNanos
    ) {
        public double totalMillis() { return totalNanos / 1_000_000.0; }
        public double minMillis() { return minNanos / 1_000_000.0; }
        public double maxMillis() { return maxNanos / 1_000_000.0; }
        public double avgMillis() { return avgNanos / 1_000_000.0; }
    }

    public record ProfileReport(Map<String, PhaseStats> phases, long totalNanos) {
        public double totalMillis() { return totalNanos / 1_000_000.0; }
    }
}
