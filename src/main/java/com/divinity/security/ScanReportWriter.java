package com.divinity.security;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ScanReportWriter {

    public static void writeTextReport(List<ClassScanReport> reports, File outputFile) throws IOException {
        StringBuilder sb = new StringBuilder();

        long threatCount = reports.stream().filter(ClassScanReport::isThreat).count();
        long highCount = reports.stream().filter(r -> r.threatLevel.ordinal() >= ThreatLevel.HIGH.ordinal()).count();
        long criticalCount = reports.stream().filter(r -> r.threatLevel.ordinal() >= ThreatLevel.CRITICAL.ordinal()).count();

        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║          DIVINITY MALWARE SCAN REPORT                    ║\n");
        sb.append("╠══════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Total classes scanned : %-30d ║\n", reports.size()));
        sb.append(String.format("║  Threat classes found  : %-30d ║\n", threatCount));
        sb.append(String.format("║  HIGH+ severity        : %-30d ║\n", highCount));
        sb.append(String.format("║  CRITICAL severity     : %-30d ║\n", criticalCount));
        sb.append("╚══════════════════════════════════════════════════════════╝\n\n");

        List<ClassScanReport> sorted = new ArrayList<>(reports);
        sorted.removeIf(ClassScanReport::isClean);
        sorted.sort((a, b) -> Integer.compare(b.totalScore, a.totalScore));

        if (sorted.isEmpty()) {
            sb.append("No threats detected. All classes are clean.\n");
        } else {
            for (int i = 0; i < sorted.size(); i++) {
                ClassScanReport r = sorted.get(i);
                String marker = switch (r.threatLevel) {
                    case MALICIOUS -> "\u001b[31m[MALICIOUS]\u001b[0m";
                    case CRITICAL  -> "\u001b[31m[CRITICAL]\u001b[0m";
                    case HIGH      -> "\u001b[33m[HIGH]\u001b[0m";
                    case MEDIUM    -> "\u001b[33m[MEDIUM]\u001b[0m";
                    case LOW       -> "\u001b[90m[LOW]\u001b[0m";
                    default        -> "[SUSPICIOUS]";
                };

                sb.append(String.format("%s %s (score: %d)\n", marker, r.className, r.totalScore));

                for (MalwareFinding f : r.findings) {
                    sb.append(String.format("  [%s] %s\n", f.detector(), f.description()));
                    if (!f.evidence().isEmpty()) {
                        sb.append(String.format("    -> %s\n", f.evidence()));
                    }
                }
                sb.append('\n');
            }
        }

        Files.writeString(outputFile.toPath(), sb.toString());
    }

    public static void writeJsonReport(List<ClassScanReport> reports, File outputFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"scanner\": \"Divinity Malware Scanner v2.0\",\n");
        sb.append("  \"totalClasses\": ").append(reports.size()).append(",\n");
        sb.append("  \"threatClasses\": ").append(reports.stream().filter(ClassScanReport::isThreat).count()).append(",\n");

        sb.append("  \"findings\": [\n");
        boolean first = true;
        for (ClassScanReport r : reports) {
            if (r.isClean()) continue;
            if (!first) sb.append(",\n");
            first = false;

            sb.append("    {\n");
            sb.append("      \"class\": \"").append(escapeJson(r.className)).append("\",\n");
            sb.append("      \"score\": ").append(r.totalScore).append(",\n");
            sb.append("      \"threatLevel\": \"").append(r.threatLevel.label).append("\",\n");
            sb.append("      \"detections\": [\n");
            for (int i = 0; i < r.findings.size(); i++) {
                var f = r.findings.get(i);
                sb.append("        {");
                sb.append("\"detector\": \"").append(escapeJson(f.detector())).append("\", ");
                sb.append("\"severity\": ").append(f.severity()).append(", ");
                sb.append("\"description\": \"").append(escapeJson(f.description())).append("\", ");
                sb.append("\"evidence\": \"").append(escapeJson(f.evidence())).append("\"");
                sb.append("}");
                if (i < r.findings.size() - 1) sb.append(",");
                sb.append('\n');
            }
            sb.append("      ]\n");
            sb.append("    }");
        }
        sb.append("\n  ]\n");
        sb.append("}\n");

        Files.writeString(outputFile.toPath(), sb.toString());
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
