package com.divinity.naming;

import com.divinity.cfg.ControlFlowGraph;
import com.divinity.semantic.SemanticAnalyzer;
import com.divinity.types.JavaType;
import java.util.*;

public final class SmartRenamer {

    private final Map<String, String> renamingMap;
    private final NameSuggestionEngine suggestionEngine;

    public SmartRenamer() {
        this.renamingMap = new LinkedHashMap<>();
        this.suggestionEngine = new NameSuggestionEngine();
    }

    public Map<String, String> generateRenamings(ControlFlowGraph cfg,
                                                  SemanticAnalyzer.SemanticAnalysisResult semantics) {
        renameVariables(cfg, semantics);
        renameMethods(semantics);
        renameClasses();

        return renamingMap;
    }

    private void renameVariables(ControlFlowGraph cfg,
                                  SemanticAnalyzer.SemanticAnalysisResult semantics) {
        Map<String, String> suggestions = new LinkedHashMap<>();

        suggestions.put("var0", "value");
        suggestions.put("var1", "index");
        suggestions.put("var2", "result");
        suggestions.put("var3", "temp");

        for (Map.Entry<String, String> entry : suggestions.entrySet()) {
            if (isObfuscatedName(entry.getKey())) {
                renamingMap.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private void renameMethods(SemanticAnalyzer.SemanticAnalysisResult semantics) {
        var purposeInfo = semantics.semantics().get("purpose");
        if (purposeInfo != null) {
            String purpose = purposeInfo.value().toString();

            if (purpose.contains("GETTER")) {
                renamingMap.put("a", "getValue");
            } else if (purpose.contains("SETTER")) {
                renamingMap.put("a", "setValue");
            } else if (purpose.contains("VALIDATOR")) {
                renamingMap.put("a", "validate");
            } else if (purpose.contains("BUILDER")) {
                renamingMap.put("a", "build");
            } else if (purpose.contains("FACTORY")) {
                renamingMap.put("a", "create");
            }
        }
    }

    private void renameClasses() {
    }

    private boolean isObfuscatedName(String name) {
        if (name.length() == 1) return true;

        if (name.matches("^[a-z]{1,3}$")) return true;

        if (name.matches("^[A-Z]{1,3}$")) return true;

        if (name.matches("^(var|arg|tmp)\\d+$")) return true;

        return false;
    }

    public static class NameSuggestionEngine {

        private final Map<String, List<String>> commonPatterns;

        public NameSuggestionEngine() {
            this.commonPatterns = new LinkedHashMap<>();
            initializePatterns();
        }

        private void initializePatterns() {
            commonPatterns.put("loop_counter", List.of("i", "j", "k", "index", "counter"));
            commonPatterns.put("collection", List.of("list", "items", "elements", "collection"));
            commonPatterns.put("map", List.of("map", "cache", "lookup", "dictionary"));
            commonPatterns.put("string", List.of("text", "message", "content", "data"));
            commonPatterns.put("number", List.of("count", "size", "length", "total"));
            commonPatterns.put("boolean", List.of("flag", "enabled", "active", "valid"));
            commonPatterns.put("result", List.of("result", "output", "value", "response"));
            commonPatterns.put("input", List.of("input", "param", "argument", "value"));
            commonPatterns.put("temp", List.of("temp", "tmp", "buffer", "scratch"));
        }

        public String suggestName(JavaType type, String context) {
            if (type instanceof JavaType.Primitive p) {
                return switch (p.name()) {
                    case "int", "long" -> "count";
                    case "boolean" -> "flag";
                    case "float", "double" -> "value";
                    default -> "var";
                };
            }

            if (type instanceof JavaType.Class c) {
                String className = c.name();

                if (className.contains("List") || className.contains("ArrayList")) {
                    return "list";
                }
                if (className.contains("Map") || className.contains("HashMap")) {
                    return "map";
                }
                if (className.contains("Set") || className.contains("HashSet")) {
                    return "set";
                }
                if (className.equals("java.lang.String")) {
                    return "text";
                }

                int lastDot = className.lastIndexOf('.');
                String simpleName = lastDot >= 0 ? className.substring(lastDot + 1) : className;
                return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
            }

            return "value";
        }

        public List<String> suggestAlternatives(String baseName) {
            List<String> alternatives = new ArrayList<>();
            alternatives.add(baseName);
            alternatives.add(baseName + "Value");
            alternatives.add(baseName + "Data");
            alternatives.add("the" + capitalize(baseName));
            return alternatives;
        }

        private String capitalize(String s) {
            if (s == null || s.isEmpty()) return s;
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }
}
