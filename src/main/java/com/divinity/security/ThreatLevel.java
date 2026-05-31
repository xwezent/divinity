package com.divinity.security;

public enum ThreatLevel {
    CLEAN(0, "CLEAN"),
    SUSPICIOUS(15, "SUSPICIOUS"),
    LOW(30, "LOW"),
    MEDIUM(50, "MEDIUM"),
    HIGH(70, "HIGH"),
    CRITICAL(85, "CRITICAL"),
    MALICIOUS(95, "MALICIOUS");

    public final int threshold;
    public final String label;

    ThreatLevel(int threshold, String label) {
        this.threshold = threshold;
        this.label = label;
    }

    public static ThreatLevel fromScore(int score) {
        if (score >= MALICIOUS.threshold) return MALICIOUS;
        if (score >= CRITICAL.threshold) return CRITICAL;
        if (score >= HIGH.threshold) return HIGH;
        if (score >= MEDIUM.threshold) return MEDIUM;
        if (score >= LOW.threshold) return LOW;
        if (score >= SUSPICIOUS.threshold) return SUSPICIOUS;
        return CLEAN;
    }
}
