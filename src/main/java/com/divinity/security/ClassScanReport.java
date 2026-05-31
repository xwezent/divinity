package com.divinity.security;

import java.util.*;

public final class ClassScanReport {
    public final String className;
    public final String sourcePath;
    public final int totalScore;
    public final ThreatLevel threatLevel;
    public final List<MalwareFinding> findings;

    public ClassScanReport(String className, String sourcePath, List<MalwareFinding> findings) {
        this.className = className;
        this.sourcePath = sourcePath;
        this.findings = List.copyOf(findings);
        this.totalScore = findings.stream().mapToInt(MalwareFinding::severity).sum();
        this.threatLevel = ThreatLevel.fromScore(Math.min(totalScore, 100));
    }

    public boolean isClean() {
        return findings.isEmpty() || threatLevel == ThreatLevel.CLEAN;
    }

    public boolean isThreat() {
        return threatLevel.ordinal() >= ThreatLevel.MEDIUM.ordinal();
    }
}
