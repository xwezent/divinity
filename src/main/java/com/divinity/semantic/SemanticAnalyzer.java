package com.divinity.semantic;

import com.divinity.ast.AstNode;
import com.divinity.cfg.ControlFlowGraph;
import com.divinity.types.JavaType;
import java.util.*;

public final class SemanticAnalyzer {

    private final ControlFlowGraph cfg;
    private final Map<String, SemanticInfo> semanticMap;
    private final CodePatternLibrary patternLibrary;

    public SemanticAnalyzer(ControlFlowGraph cfg) {
        this.cfg = cfg;
        this.semanticMap = new LinkedHashMap<>();
        this.patternLibrary = new CodePatternLibrary();
    }

    public SemanticAnalysisResult analyze() {
        detectPurpose();
        detectPatterns();
        inferVariableNames();
        detectAlgorithms();
        computeComplexity();
        detectCodeSmells();

        return new SemanticAnalysisResult(semanticMap, patternLibrary.getDetectedPatterns());
    }

    private void detectPurpose() {
        MethodPurpose purpose = inferMethodPurpose();
        semanticMap.put("purpose", new SemanticInfo("purpose", purpose.toString()));
    }

    private MethodPurpose inferMethodPurpose() {
        int getterScore = 0;
        int setterScore = 0;
        int validatorScore = 0;
        int builderScore = 0;
        int factoryScore = 0;

        if (cfg.getBlocks().size() <= 3) {
            getterScore += 20;
        }

        if (hasReturnStatement() && !hasComplexLogic()) {
            getterScore += 15;
        }

        if (hasFieldAssignment() && cfg.getBlocks().size() <= 5) {
            setterScore += 20;
        }

        if (hasConditionalChecks() && hasThrowStatement()) {
            validatorScore += 25;
        }

        if (hasFluentReturn() && hasFieldAssignments()) {
            builderScore += 30;
        }

        if (hasNewInstance() && hasReturnStatement()) {
            factoryScore += 25;
        }

        int maxScore = Math.max(getterScore, Math.max(setterScore,
            Math.max(validatorScore, Math.max(builderScore, factoryScore))));

        if (maxScore == getterScore && getterScore >= 20) return MethodPurpose.GETTER;
        if (maxScore == setterScore && setterScore >= 20) return MethodPurpose.SETTER;
        if (maxScore == validatorScore && validatorScore >= 20) return MethodPurpose.VALIDATOR;
        if (maxScore == builderScore && builderScore >= 20) return MethodPurpose.BUILDER;
        if (maxScore == factoryScore && factoryScore >= 20) return MethodPurpose.FACTORY;

        return MethodPurpose.BUSINESS_LOGIC;
    }

    private void detectPatterns() {
        if (isSingletonPattern()) {
            patternLibrary.addPattern(new CodePattern("Singleton", "Creational pattern"));
        }

        if (isFactoryPattern()) {
            patternLibrary.addPattern(new CodePattern("Factory", "Creational pattern"));
        }

        if (isBuilderPattern()) {
            patternLibrary.addPattern(new CodePattern("Builder", "Creational pattern"));
        }

        if (isObserverPattern()) {
            patternLibrary.addPattern(new CodePattern("Observer", "Behavioral pattern"));
        }

        if (isStrategyPattern()) {
            patternLibrary.addPattern(new CodePattern("Strategy", "Behavioral pattern"));
        }
    }

    private void inferVariableNames() {
        Map<String, String> suggestions = new LinkedHashMap<>();

        suggestions.put("var0", inferNameFromUsage("var0"));
        suggestions.put("var1", inferNameFromUsage("var1"));

        semanticMap.put("variable_names", new SemanticInfo("names", suggestions));
    }

    private String inferNameFromUsage(String varName) {
        return "value";
    }

    private void detectAlgorithms() {
        if (isSortingAlgorithm()) {
            semanticMap.put("algorithm", new SemanticInfo("algorithm", "Sorting"));
        } else if (isSearchAlgorithm()) {
            semanticMap.put("algorithm", new SemanticInfo("algorithm", "Search"));
        } else if (isEncryptionAlgorithm()) {
            semanticMap.put("algorithm", new SemanticInfo("algorithm", "Encryption"));
        } else if (isHashingAlgorithm()) {
            semanticMap.put("algorithm", new SemanticInfo("algorithm", "Hashing"));
        }
    }

    private void computeComplexity() {
        int cyclomaticComplexity = computeCyclomaticComplexity();
        int cognitiveComplexity = computeCognitiveComplexity();

        semanticMap.put("cyclomatic", new SemanticInfo("complexity", cyclomaticComplexity));
        semanticMap.put("cognitive", new SemanticInfo("complexity", cognitiveComplexity));
    }

    private void detectCodeSmells() {
        List<CodeSmell> smells = new ArrayList<>();

        if (cfg.getBlocks().size() > 50) {
            smells.add(new CodeSmell("Long Method", "Method has too many blocks"));
        }

        if (computeCyclomaticComplexity() > 20) {
            smells.add(new CodeSmell("High Complexity", "Cyclomatic complexity > 20"));
        }

        semanticMap.put("code_smells", new SemanticInfo("smells", smells));
    }

    private boolean hasReturnStatement() {
        return cfg.getBlocks().stream()
            .anyMatch(block -> block.instructions.stream()
                .anyMatch(inst -> inst.opcode.isReturn()));
    }

    private boolean hasComplexLogic() {
        return cfg.getBlocks().size() > 5;
    }

    private boolean hasFieldAssignment() {
        return false;
    }

    private boolean hasFieldAssignments() {
        return false;
    }

    private boolean hasConditionalChecks() {
        return cfg.getBlocks().stream()
            .anyMatch(block -> block.instructions.stream()
                .anyMatch(inst -> inst.opcode.isConditionalBranch()));
    }

    private boolean hasThrowStatement() {
        return false;
    }

    private boolean hasFluentReturn() {
        return false;
    }

    private boolean hasNewInstance() {
        return false;
    }

    private boolean isSingletonPattern() {
        return false;
    }

    private boolean isFactoryPattern() {
        return hasNewInstance() && hasReturnStatement();
    }

    private boolean isBuilderPattern() {
        return hasFluentReturn();
    }

    private boolean isObserverPattern() {
        return false;
    }

    private boolean isStrategyPattern() {
        return false;
    }

    private boolean isSortingAlgorithm() {
        return false;
    }

    private boolean isSearchAlgorithm() {
        return false;
    }

    private boolean isEncryptionAlgorithm() {
        return false;
    }

    private boolean isHashingAlgorithm() {
        return false;
    }

    private int computeCyclomaticComplexity() {
        int edges = 0;
        int nodes = cfg.getBlocks().size();

        for (var block : cfg.getBlocks()) {
            edges += block.successors.size();
        }

        return edges - nodes + 2;
    }

    private int computeCognitiveComplexity() {
        int complexity = 0;
        int nestingLevel = 0;

        for (var block : cfg.getBlocks()) {
            if (block.successors.size() > 1) {
                complexity += (1 + nestingLevel);
            }
        }

        return complexity;
    }

    public enum MethodPurpose {
        GETTER,
        SETTER,
        VALIDATOR,
        BUILDER,
        FACTORY,
        BUSINESS_LOGIC,
        UTILITY,
        CALLBACK,
        UNKNOWN
    }

    public record SemanticInfo(String category, Object value) {}

    public record CodePattern(String name, String description) {}

    public record CodeSmell(String name, String description) {}

    public record SemanticAnalysisResult(Map<String, SemanticInfo> semantics,
                                         List<CodePattern> patterns) {}

    public static class CodePatternLibrary {
        private final List<CodePattern> detectedPatterns = new ArrayList<>();

        public void addPattern(CodePattern pattern) {
            detectedPatterns.add(pattern);
        }

        public List<CodePattern> getDetectedPatterns() {
            return detectedPatterns;
        }
    }
}
