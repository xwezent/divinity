package com.divinity.deobfuscation;

import com.divinity.ast.AstNode;
import java.util.*;

public final class MbaSimplifier {

    private static final Map<String, MbaPattern> PATTERNS = new LinkedHashMap<>();

    static {
        // XOR patterns: a ^ b
        PATTERNS.put("xor1", new MbaPattern(
            "(a | b) - (a & b)",
            "a ^ b"
        ));
        PATTERNS.put("xor2", new MbaPattern(
            "(a + b) - (2 * (a & b))",
            "a ^ b"
        ));
        PATTERNS.put("xor3", new MbaPattern(
            "(a | b) & ((a & b) ^ -1)",
            "a ^ b"
        ));
        PATTERNS.put("xor4", new MbaPattern(
            "(a & (b ^ -1)) | ((a ^ -1) & b)",
            "a ^ b"
        ));

        // OR patterns: a | b
        PATTERNS.put("or1", new MbaPattern(
            "(a ^ b) + (2 * (a & b))",
            "a | b"
        ));
        PATTERNS.put("or2", new MbaPattern(
            "(a + b) - (a & b)",
            "a | b"
        ));

        // AND patterns: a & b
        PATTERNS.put("and1", new MbaPattern(
            "(a + b) - (a | b)",
            "a & b"
        ));
        PATTERNS.put("and2", new MbaPattern(
            "(a | b) - (a ^ b)",
            "a & b"
        ));

        // NOT patterns: ~a
        PATTERNS.put("not1", new MbaPattern(
            "a ^ -1",
            "~a"
        ));
        PATTERNS.put("not2", new MbaPattern(
            "-1 - a",
            "~a"
        ));

        // Identity patterns
        PATTERNS.put("identity1", new MbaPattern(
            "a + 0",
            "a"
        ));
        PATTERNS.put("identity2", new MbaPattern(
            "a * 1",
            "a"
        ));
        PATTERNS.put("identity3", new MbaPattern(
            "a | 0",
            "a"
        ));
        PATTERNS.put("identity4", new MbaPattern(
            "a & -1",
            "a"
        ));

        // Zero patterns
        PATTERNS.put("zero1", new MbaPattern(
            "a - a",
            "0"
        ));
        PATTERNS.put("zero2", new MbaPattern(
            "a ^ a",
            "0"
        ));
        PATTERNS.put("zero3", new MbaPattern(
            "a & 0",
            "0"
        ));
    }

    public AstNode.Expression simplify(AstNode.Expression expr) {
        if (expr == null) return null;

        AstNode.Expression simplified = simplifyOnce(expr);

        int maxIterations = 10;
        int iterations = 0;
        while (!simplified.equals(expr) && iterations < maxIterations) {
            expr = simplified;
            simplified = simplifyOnce(expr);
            iterations++;
        }

        return simplified;
    }

    private AstNode.Expression simplifyOnce(AstNode.Expression expr) {
        if (expr instanceof AstNode.Expression.BinaryExpr binary) {
            AstNode.Expression left = simplify(binary.left());
            AstNode.Expression right = simplify(binary.right());

            for (MbaPattern pattern : PATTERNS.values()) {
                AstNode.Expression result = pattern.tryMatch(left, binary.operator(), right);
                if (result != null) {
                    return result;
                }
            }

            if (!left.equals(binary.left()) || !right.equals(binary.right())) {
                return new AstNode.Expression.BinaryExpr(binary.operator(), left, right);
            }
        }

        return expr;
    }

    private static class MbaPattern {
        private final String pattern;
        private final String replacement;

        MbaPattern(String pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }

        AstNode.Expression tryMatch(AstNode.Expression left, String op, AstNode.Expression right) {
            // (a | b) - (a & b) => a ^ b
            if (pattern.equals("(a | b) - (a & b)") && op.equals("-")) {
                if (left instanceof AstNode.Expression.BinaryExpr leftBin && leftBin.operator().equals("|") &&
                    right instanceof AstNode.Expression.BinaryExpr rightBin && rightBin.operator().equals("&")) {

                    if (expressionsEqual(leftBin.left(), rightBin.left()) &&
                        expressionsEqual(leftBin.right(), rightBin.right())) {
                        return new AstNode.Expression.BinaryExpr("^", leftBin.left(), leftBin.right());
                    }
                }
            }

            // (a + b) - (2 * (a & b)) => a ^ b
            if (pattern.equals("(a + b) - (2 * (a & b))") && op.equals("-")) {
                if (left instanceof AstNode.Expression.BinaryExpr leftBin && leftBin.operator().equals("+") &&
                    right instanceof AstNode.Expression.BinaryExpr rightBin && rightBin.operator().equals("*")) {

                    if (rightBin.left() instanceof AstNode.Expression.LiteralExpr lit &&
                        lit.value().equals(2)) {
                        if (rightBin.right() instanceof AstNode.Expression.BinaryExpr andExpr &&
                            andExpr.operator().equals("&")) {
                            if (expressionsEqual(leftBin.left(), andExpr.left()) &&
                                expressionsEqual(leftBin.right(), andExpr.right())) {
                                return new AstNode.Expression.BinaryExpr("^", leftBin.left(), leftBin.right());
                            }
                        }
                    }
                }
            }

            // (a | b) & ((a & b) ^ -1) => a ^ b
            if (pattern.equals("(a | b) & ((a & b) ^ -1)") && op.equals("&")) {
                if (left instanceof AstNode.Expression.BinaryExpr leftBin && leftBin.operator().equals("|") &&
                    right instanceof AstNode.Expression.BinaryExpr rightBin && rightBin.operator().equals("^")) {

                    if (rightBin.right() instanceof AstNode.Expression.LiteralExpr lit &&
                        lit.value().equals(-1)) {
                        if (rightBin.left() instanceof AstNode.Expression.BinaryExpr andExpr &&
                            andExpr.operator().equals("&")) {
                            if (expressionsEqual(leftBin.left(), andExpr.left()) &&
                                expressionsEqual(leftBin.right(), andExpr.right())) {
                                return new AstNode.Expression.BinaryExpr("^", leftBin.left(), leftBin.right());
                            }
                        }
                    }
                }
            }

            // (a & (b ^ -1)) | ((a ^ -1) & b) => a ^ b
            if (pattern.equals("(a & (b ^ -1)) | ((a ^ -1) & b)") && op.equals("|")) {
                if (left instanceof AstNode.Expression.BinaryExpr leftBin && leftBin.operator().equals("&") &&
                    right instanceof AstNode.Expression.BinaryExpr rightBin && rightBin.operator().equals("&")) {

                    if (leftBin.right() instanceof AstNode.Expression.BinaryExpr leftXor &&
                        leftXor.operator().equals("^") &&
                        rightBin.left() instanceof AstNode.Expression.BinaryExpr rightXor &&
                        rightXor.operator().equals("^")) {

                        if (leftXor.right() instanceof AstNode.Expression.LiteralExpr lit1 &&
                            lit1.value().equals(-1) &&
                            rightXor.right() instanceof AstNode.Expression.LiteralExpr lit2 &&
                            lit2.value().equals(-1)) {

                            if (expressionsEqual(leftBin.left(), rightXor.left()) &&
                                expressionsEqual(leftXor.left(), rightBin.right())) {
                                return new AstNode.Expression.BinaryExpr("^", leftBin.left(), leftXor.left());
                            }
                        }
                    }
                }
            }

            // Identity: a + 0 => a
            if (pattern.equals("a + 0") && op.equals("+")) {
                if (right instanceof AstNode.Expression.LiteralExpr lit && lit.value().equals(0)) {
                    return left;
                }
                if (left instanceof AstNode.Expression.LiteralExpr lit && lit.value().equals(0)) {
                    return right;
                }
            }

            // Identity: a * 1 => a
            if (pattern.equals("a * 1") && op.equals("*")) {
                if (right instanceof AstNode.Expression.LiteralExpr lit && lit.value().equals(1)) {
                    return left;
                }
                if (left instanceof AstNode.Expression.LiteralExpr lit && lit.value().equals(1)) {
                    return right;
                }
            }

            // Zero: a - a => 0
            if (pattern.equals("a - a") && op.equals("-")) {
                if (expressionsEqual(left, right)) {
                    return new AstNode.Expression.LiteralExpr(0, "I");
                }
            }

            // Zero: a ^ a => 0
            if (pattern.equals("a ^ a") && op.equals("^")) {
                if (expressionsEqual(left, right)) {
                    return new AstNode.Expression.LiteralExpr(0, "I");
                }
            }

            return null;
        }

        private boolean expressionsEqual(AstNode.Expression e1, AstNode.Expression e2) {
            if (e1 == e2) return true;
            if (e1 == null || e2 == null) return false;

            if (e1 instanceof AstNode.Expression.VarExpr v1 &&
                e2 instanceof AstNode.Expression.VarExpr v2) {
                return v1.name().equals(v2.name());
            }

            if (e1 instanceof AstNode.Expression.LiteralExpr l1 &&
                e2 instanceof AstNode.Expression.LiteralExpr l2) {
                return Objects.equals(l1.value(), l2.value());
            }

            if (e1 instanceof AstNode.Expression.BinaryExpr b1 &&
                e2 instanceof AstNode.Expression.BinaryExpr b2) {
                return b1.operator().equals(b2.operator()) &&
                       expressionsEqual(b1.left(), b2.left()) &&
                       expressionsEqual(b1.right(), b2.right());
            }

            return false;
        }
    }

    public static class SimplificationStats {
        private int totalExpressions;
        private int simplifiedExpressions;
        private final Map<String, Integer> patternUsage = new LinkedHashMap<>();

        public void recordSimplification(String patternName) {
            simplifiedExpressions++;
            patternUsage.merge(patternName, 1, Integer::sum);
        }

        public void recordExpression() {
            totalExpressions++;
        }

        public double getSimplificationRate() {
            return totalExpressions == 0 ? 0.0 :
                   (double) simplifiedExpressions / totalExpressions * 100.0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("MBA Simplification Stats:\n");
            sb.append(String.format("  Total expressions: %d\n", totalExpressions));
            sb.append(String.format("  Simplified: %d (%.1f%%)\n",
                simplifiedExpressions, getSimplificationRate()));

            if (!patternUsage.isEmpty()) {
                sb.append("  Pattern usage:\n");
                patternUsage.forEach((pattern, count) ->
                    sb.append(String.format("    %s: %d\n", pattern, count)));
            }

            return sb.toString();
        }
    }
}
