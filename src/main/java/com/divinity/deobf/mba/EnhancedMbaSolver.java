package com.divinity.deobf.mba;

import java.util.*;

public final class EnhancedMbaSolver {

    private final boolean useZ3;
    private final List<MbaPattern.KnownMba> knownPatterns;

    public EnhancedMbaSolver() {
        this.useZ3 = Z3MbaSolver.isAvailable();
        this.knownPatterns = MbaPattern.getKnownMbas();
    }

    public MbaSolver.Node simplify(MbaSolver.Node node) {
        MbaSolver.Node result = node;

        result = MbaSolver.simplify(result);

        result = tryPatternMatching(result);

        if (useZ3) {
            MbaSolver.Node z3Result = Z3MbaSolver.simplify(result);
            if (z3Result != null && isSimpler(z3Result, result)) {
                result = z3Result;
            }
        }

        result = MbaSolver.simplify(result);

        return result;
    }

    private MbaSolver.Node tryPatternMatching(MbaSolver.Node node) {
        MbaSolver.Node current = node;
        boolean changed = true;
        int iterations = 0;

        while (changed && iterations < 5) {
            changed = false;
            iterations++;

            MbaSolver.Node patternResult = MbaPattern.tryMatch(current);
            if (patternResult != null && !patternResult.equals(current)) {
                current = patternResult;
                changed = true;
                continue;
            }

            if (current instanceof MbaSolver.Node.Op op) {
                MbaSolver.Node leftSimplified = tryPatternMatching(op.left());
                MbaSolver.Node rightSimplified = op.right() != null ?
                    tryPatternMatching(op.right()) : null;

                if (!leftSimplified.equals(op.left()) ||
                    (rightSimplified != null && !rightSimplified.equals(op.right()))) {
                    current = new MbaSolver.Node.Op(op.kind(), leftSimplified, rightSimplified);
                    changed = true;
                }
            }
        }

        return current;
    }

    private boolean isSimpler(MbaSolver.Node a, MbaSolver.Node b) {
        int complexityA = computeComplexity(a);
        int complexityB = computeComplexity(b);
        return complexityA < complexityB;
    }

    private int computeComplexity(MbaSolver.Node node) {
        return switch (node) {
            case MbaSolver.Node.Constant c -> 1;
            case MbaSolver.Node.Variable v -> 1;
            case MbaSolver.Node.Op op -> {
                int leftComplexity = computeComplexity(op.left());
                int rightComplexity = op.right() != null ? computeComplexity(op.right()) : 0;
                int opCost = switch (op.kind()) {
                    case MbaSolver.NOT, MbaSolver.NEG -> 1;
                    case MbaSolver.AND, MbaSolver.OR, MbaSolver.XOR -> 2;
                    case MbaSolver.ADD, MbaSolver.SUB -> 3;
                    case MbaSolver.MUL -> 5;
                    case MbaSolver.SHL, MbaSolver.SHR, MbaSolver.USHR -> 2;
                    default -> 1;
                };
                yield leftComplexity + rightComplexity + opCost;
            }
        };
    }

    public List<MbaSolver.Node> findEquivalentExpressions(MbaSolver.Node node) {
        List<MbaSolver.Node> equivalents = new ArrayList<>();

        for (MbaPattern.KnownMba pattern : knownPatterns) {
            if (useZ3) {
                MbaSolver.Node candidate = parseExpression(pattern.simplified());
                if (candidate != null) {
                    MbaSolver.Node result = Z3MbaSolver.solveEquivalence(node, candidate);
                    if (result != null) {
                        equivalents.add(result);
                    }
                }
            }
        }

        return equivalents;
    }

    private MbaSolver.Node parseExpression(String expr) {
        return null;
    }

    public String explainSimplification(MbaSolver.Node original, MbaSolver.Node simplified) {
        StringBuilder explanation = new StringBuilder();
        explanation.append("Original: ").append(original.toString()).append("\n");
        explanation.append("Simplified: ").append(simplified.toString()).append("\n");
        explanation.append("Complexity reduction: ")
                .append(computeComplexity(original))
                .append(" -> ")
                .append(computeComplexity(simplified))
                .append("\n");

        for (MbaPattern.KnownMba pattern : knownPatterns) {
            if (simplified.toString().contains(pattern.simplified())) {
                explanation.append("Applied pattern: ").append(pattern.description()).append("\n");
            }
        }

        return explanation.toString();
    }

    public boolean verifyEquivalence(MbaSolver.Node expr1, MbaSolver.Node expr2) {
        Set<MbaSolver.Node.Variable> vars = new LinkedHashSet<>();
        collectVars(expr1, vars);
        collectVars(expr2, vars);

        if (vars.size() > 4) {
            if (useZ3) {
                MbaSolver.Node result = Z3MbaSolver.solveEquivalence(expr1, expr2);
                return result != null;
            }
            return false;
        }

        List<MbaSolver.Node.Variable> varList = new ArrayList<>(vars);
        int combinations = 1 << varList.size();

        for (int i = 0; i < combinations; i++) {
            Map<Integer, Integer> env = new LinkedHashMap<>();
            for (int j = 0; j < varList.size(); j++) {
                env.put(varList.get(j).id(), (i >> j) & 1);
            }

            int val1 = MbaSolver.evaluate(expr1, env);
            int val2 = MbaSolver.evaluate(expr2, env);

            if (val1 != val2) {
                return false;
            }
        }

        return true;
    }

    private void collectVars(MbaSolver.Node node, Set<MbaSolver.Node.Variable> vars) {
        switch (node) {
            case MbaSolver.Node.Variable v -> vars.add(v);
            case MbaSolver.Node.Op op -> {
                if (op.left() != null) collectVars(op.left(), vars);
                if (op.right() != null) collectVars(op.right(), vars);
            }
            default -> {}
        }
    }
}
