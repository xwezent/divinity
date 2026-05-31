package com.divinity.quality;

import com.divinity.ast.AstNode;
import com.divinity.cfg.ControlFlowGraph;
import java.util.*;

public final class CodeQualityAnalyzer {

    private final ControlFlowGraph cfg;
    private final AstNode.ClassNode classNode;

    public CodeQualityAnalyzer(ControlFlowGraph cfg, AstNode.ClassNode classNode) {
        this.cfg = cfg;
        this.classNode = classNode;
    }

    public QualityReport analyze() {
        double readabilityScore = computeReadability();
        double maintainabilityScore = computeMaintainability();
        double compilabilityScore = computeCompilability();
        double semanticScore = computeSemanticCorrectness();

        List<QualityIssue> issues = detectIssues();
        List<String> suggestions = generateSuggestions(issues);

        double overallScore = (readabilityScore + maintainabilityScore +
                              compilabilityScore + semanticScore) / 4.0;

        return new QualityReport(
            overallScore,
            readabilityScore,
            maintainabilityScore,
            compilabilityScore,
            semanticScore,
            issues,
            suggestions
        );
    }

    private double computeReadability() {
        double score = 100.0;

        int obfuscatedNames = countObfuscatedNames();
        score -= obfuscatedNames * 2.0;

        int complexity = cfg.getBlocks().size();
        if (complexity > 50) score -= (complexity - 50) * 0.5;

        int nestingDepth = computeMaxNestingDepth();
        if (nestingDepth > 5) score -= (nestingDepth - 5) * 5.0;

        return Math.max(0, Math.min(100, score));
    }

    private double computeMaintainability() {
        double score = 100.0;

        int cyclomaticComplexity = computeCyclomaticComplexity();
        if (cyclomaticComplexity > 10) {
            score -= (cyclomaticComplexity - 10) * 2.0;
        }

        int methodLength = cfg.getBlocks().size();
        if (methodLength > 30) {
            score -= (methodLength - 30) * 1.0;
        }

        return Math.max(0, Math.min(100, score));
    }

    private double computeCompilability() {
        double score = 100.0;

        if (hasSyntaxErrors()) score -= 50.0;
        if (hasTypeErrors()) score -= 30.0;
        if (hasMissingImports()) score -= 10.0;

        return Math.max(0, Math.min(100, score));
    }

    private double computeSemanticCorrectness() {
        double score = 100.0;

        if (hasUnreachableCode()) score -= 15.0;
        if (hasDeadCode()) score -= 10.0;
        if (hasInconsistentTypes()) score -= 20.0;

        return Math.max(0, Math.min(100, score));
    }

    private List<QualityIssue> detectIssues() {
        List<QualityIssue> issues = new ArrayList<>();

        if (computeCyclomaticComplexity() > 15) {
            issues.add(new QualityIssue(
                IssueType.HIGH_COMPLEXITY,
                "High cyclomatic complexity",
                IssueSeverity.WARNING
            ));
        }

        if (countObfuscatedNames() > 5) {
            issues.add(new QualityIssue(
                IssueType.OBFUSCATED_NAMES,
                "Many obfuscated variable names",
                IssueSeverity.INFO
            ));
        }

        if (cfg.getBlocks().size() > 50) {
            issues.add(new QualityIssue(
                IssueType.LONG_METHOD,
                "Method is too long",
                IssueSeverity.WARNING
            ));
        }

        return issues;
    }

    private List<String> generateSuggestions(List<QualityIssue> issues) {
        List<String> suggestions = new ArrayList<>();

        for (QualityIssue issue : issues) {
            switch (issue.type()) {
                case HIGH_COMPLEXITY -> suggestions.add(
                    "Consider breaking down complex logic into smaller methods");
                case OBFUSCATED_NAMES -> suggestions.add(
                    "Use smart renaming to improve variable names");
                case LONG_METHOD -> suggestions.add(
                    "Extract parts of this method into separate methods");
                case DEAD_CODE -> suggestions.add(
                    "Remove unreachable code blocks");
            }
        }

        return suggestions;
    }

    private int countObfuscatedNames() {
        int count = 0;
        return count;
    }

    private int computeMaxNestingDepth() {
        return 3;
    }

    private int computeCyclomaticComplexity() {
        int edges = 0;
        int nodes = cfg.getBlocks().size();

        for (var block : cfg.getBlocks()) {
            edges += block.successors.size();
        }

        return edges - nodes + 2;
    }

    private boolean hasSyntaxErrors() {
        return false;
    }

    private boolean hasTypeErrors() {
        return false;
    }

    private boolean hasMissingImports() {
        return false;
    }

    private boolean hasUnreachableCode() {
        return false;
    }

    private boolean hasDeadCode() {
        return false;
    }

    private boolean hasInconsistentTypes() {
        return false;
    }

    public enum IssueType {
        HIGH_COMPLEXITY,
        OBFUSCATED_NAMES,
        LONG_METHOD,
        DEAD_CODE,
        TYPE_MISMATCH,
        MISSING_IMPORTS,
        SYNTAX_ERROR
    }

    public enum IssueSeverity {
        ERROR,
        WARNING,
        INFO
    }

    public record QualityIssue(IssueType type, String description, IssueSeverity severity) {}

    public record QualityReport(
        double overallScore,
        double readabilityScore,
        double maintainabilityScore,
        double compilabilityScore,
        double semanticScore,
        List<QualityIssue> issues,
        List<String> suggestions
    ) {
        public String getGrade() {
            if (overallScore >= 90) return "A";
            if (overallScore >= 80) return "B";
            if (overallScore >= 70) return "C";
            if (overallScore >= 60) return "D";
            return "F";
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Code Quality Report\n");
            sb.append("===================\n");
            sb.append(String.format("Overall Score: %.1f%% (Grade: %s)\n",
                overallScore, getGrade()));
            sb.append(String.format("  Readability:     %.1f%%\n", readabilityScore));
            sb.append(String.format("  Maintainability: %.1f%%\n", maintainabilityScore));
            sb.append(String.format("  Compilability:   %.1f%%\n", compilabilityScore));
            sb.append(String.format("  Semantic:        %.1f%%\n", semanticScore));

            if (!issues.isEmpty()) {
                sb.append("\nIssues Found:\n");
                for (QualityIssue issue : issues) {
                    sb.append(String.format("  [%s] %s\n",
                        issue.severity(), issue.description()));
                }
            }

            if (!suggestions.isEmpty()) {
                sb.append("\nSuggestions:\n");
                for (String suggestion : suggestions) {
                    sb.append("  • ").append(suggestion).append("\n");
                }
            }

            return sb.toString();
        }
    }
}
