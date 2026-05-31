package com.divinity;

import com.divinity.classfile.ClassFileParser;
import com.divinity.ast.AstBuilder;
import com.divinity.ast.AstNode;
import com.divinity.writer.JavaSourceWriter;
import com.divinity.util.JarHandler;
import com.divinity.security.MalwareScanner;
import com.divinity.security.ClassScanReport;
import com.divinity.security.ScanReportWriter;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class DivinityDecompiler {

    public record DecompilerConfig(
        boolean verbose,
        int threads,
        boolean aggressive,
        boolean semantic,
        boolean smartRename,
        boolean quality,
        boolean incremental,
        Path cacheDir
    ) {}

    private final File input;
    private final File output;
    private final DecompilerConfig config;
    private final AtomicInteger classCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final ConcurrentMap<String, String> decompiledSources = new ConcurrentHashMap<>();
    private volatile long startTime;
    private volatile int totalClasses;

    public DivinityDecompiler(File input, File output, DecompilerConfig config) {
        this.input = input;
        this.output = output;
        this.config = config;
    }

    public DivinityDecompiler(File input, File output, boolean verbose, int threads) {
        this(input, output, new DecompilerConfig(verbose, threads, false, false, false, false, false, null));
    }

    public int run() throws IOException {
        startTime = System.currentTimeMillis();

        List<JarHandler.ClazzEntry> classFiles;
        boolean isJar = input.isFile() && input.getName().endsWith(".jar");

        if (isJar) {
            log("Opening JAR: " + input.getAbsolutePath());
            classFiles = JarHandler.listClassFiles(input);
        } else if (input.isDirectory()) {
            log("Scanning directory: " + input.getAbsolutePath());
            classFiles = JarHandler.findClassFiles(input);
        } else if (input.isFile() && input.getName().endsWith(".class")) {
            classFiles = List.of(new JarHandler.ClazzEntry(input.getName(), Files.readAllBytes(input.toPath())));
        } else {
            System.err.println("Error: Input must be .jar, .class, or directory");
            return 1;
        }

        totalClasses = classFiles.size();
        log("Found " + totalClasses + " class files, using " + config.threads() + " threads");

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, config.threads()));
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger(0);

        for (var entry : classFiles) {
            futures.add(executor.submit(() -> {
                try {
                    decompileClass(entry);
                    int done = completed.incrementAndGet();
                    if (!config.verbose() && done % Math.max(1, totalClasses / 20) == 0) {
                        double pct = 100.0 * done / totalClasses;
                        System.out.printf("\r  Progress: %d/%d (%.0f%%)", done, totalClasses, pct);
                    }
                    classCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    if (config.verbose()) {
                        System.err.println("  [FAIL] " + entry.name() + ": " + e.getMessage());
                    }
                    errorFallback(entry);
                }
            }));
        }

        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) { errorCount.incrementAndGet(); }
        }
        executor.shutdown();
        try { executor.awaitTermination(60, TimeUnit.SECONDS); } catch (InterruptedException e) {}
        System.out.println();

        if (output.getName().endsWith(".jar") || (isJar && output.getName().endsWith(".jar"))) {
            writeOutputJar();
        } else {
            writeOutputDirectory();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log(String.format("Done: %d classes, %d errors, %dms (%.0f cls/s)",
                classCount.get(), errorCount.get(), elapsed,
                classCount.get() * 1000.0 / Math.max(1, elapsed)));

        return errorCount.get() > 0 ? 2 : 0;
    }

    public void scanForMalware(File scanDir, File reportOutput, int threads) throws IOException {
        log("Starting malware scan on: " + scanDir.getAbsolutePath());

        MalwareScanner scanner = new MalwareScanner(config.verbose());
        List<ClassScanReport> reports = scanner.scanDirectory(scanDir, threads,
            done -> System.out.printf("\r  Scan progress: %d files%c", done, done >= totalClasses ? '\n' : '\r'));

        if (reports.isEmpty()) {
            log("Scan: no threats detected");
            return;
        }

        File reportFile = reportOutput;
        if (!reportFile.getName().contains(".")) {
            reportFile = new File(reportOutput, "divinity-scan-report.txt");
        }

        ScanReportWriter.writeTextReport(reports, reportFile);

        String jsonPath = reportFile.getAbsolutePath().replace(".txt", ".json");
        ScanReportWriter.writeJsonReport(reports, new File(jsonPath));

        long threatCount = reports.stream().filter(ClassScanReport::isThreat).count();
        long highCount = reports.stream().filter(r -> r.threatLevel.ordinal() >= com.divinity.security.ThreatLevel.HIGH.ordinal()).count();

        log("Scan complete: " + reports.size() + " classes, " + threatCount + " threats, " + highCount + " HIGH+");
        log("Report: " + reportFile.getAbsolutePath());
    }

    private void decompileClass(JarHandler.ClazzEntry entry) throws Exception {
        ClassFileParser parser = new ClassFileParser(entry.data());
        AstBuilder astBuilder = new AstBuilder(parser);
        AstNode.ClassNode ast = astBuilder.build();

        String packageName = ast.name().contains(".")
                ? ast.name().substring(0, ast.name().lastIndexOf('.')) : "";
        String simpleName = ast.name().contains(".")
                ? ast.name().substring(ast.name().lastIndexOf('.') + 1) : ast.name();
        if (simpleName.contains("$")) {
            simpleName = simpleName.substring(simpleName.lastIndexOf('$') + 1);
        }

        JavaSourceWriter writer = new JavaSourceWriter(
                packageName.isEmpty() ? null : packageName, simpleName);
        String source = writer.write(ast);
        String sourcePath = entry.name().replace(".class", ".java");
        decompiledSources.put(sourcePath, source);
    }

    private void errorFallback(JarHandler.ClazzEntry entry) {
        String sourcePath = entry.name().replace(".class", ".java");
        String packageName = "";
        String simpleName = entry.name();
        int lastSlash = entry.name().lastIndexOf('/');
        if (lastSlash >= 0) {
            packageName = entry.name().substring(0, lastSlash).replace('/', '.');
            simpleName = entry.name().substring(lastSlash + 1);
        }
        simpleName = simpleName.replace(".class", "");
        if (simpleName.contains("$")) {
            simpleName = simpleName.substring(simpleName.lastIndexOf('$') + 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// Decompiled by Divinity v2.0.0 - FALLBACK MODE\n\n");
        if (!packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        sb.append("public class ").append(simpleName).append(" {\n");
        sb.append("    // Decompilation failed\n");
        sb.append("}\n");

        decompiledSources.put(sourcePath, sb.toString());
    }

    private void writeOutputDirectory() throws IOException {
        for (var entry : decompiledSources.entrySet()) {
            File outFile = new File(output, entry.getKey().replace('/', File.separatorChar));
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Files.writeString(outFile.toPath(), entry.getValue(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private void writeOutputJar() throws IOException {
        Map<String, byte[]> jarEntries = new LinkedHashMap<>();
        for (var entry : decompiledSources.entrySet()) {
            jarEntries.put(entry.getKey(), entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        JarHandler.writeJar(output, jarEntries);
    }

    private static void log(String msg) {
        System.out.println("[divinity] " + msg);
    }
}
