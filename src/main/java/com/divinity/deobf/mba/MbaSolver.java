package com.divinity.deobf.mba;

import java.util.*;

public final class MbaSolver {

    public static final int BIT = 0;
    public static final int NOT = 1;
    public static final int AND = 2;
    public static final int OR  = 3;
    public static final int XOR = 4;
    public static final int ADD = 5;
    public static final int SUB = 6;
    public static final int MUL = 7;
    public static final int NEG = 8;
    public static final int SHL = 9;
    public static final int SHR = 10;
    public static final int USHR = 11;

    public sealed interface Node {
        record Constant(int value) implements Node {
            @Override public String toString() { return String.valueOf(value); }
        }
        record Variable(int id) implements Node {
            @Override public String toString() { return "v" + id; }
        }
        record Op(int kind, Node left, Node right) implements Node {

            public Op(int kind, Node left) {
                this(kind, left, null);
            }

            @Override public String toString() {
                return switch(kind) {
                    case NOT -> "(~" + left + ")";
                    case NEG -> "(-" + left + ")";
                    case AND -> "(" + left + " & " + right + ")";
                    case OR  -> "(" + left + " | " + right + ")";
                    case XOR -> "(" + left + " ^ " + right + ")";
                    case ADD -> "(" + left + " + " + right + ")";
                    case SUB -> "(" + left + " - " + right + ")";
                    case MUL -> "(" + left + " * " + right + ")";
                    case SHL -> "(" + left + " << " + right + ")";
                    case SHR -> "(" + left + " >> " + right + ")";
                    case USHR -> "(" + left + " >>> " + right + ")";
                    default -> "(?" + left + "?" + right + "?)";
                };
            }
        }
    }

    public static Node simplify(Node node) {
        node = canonicalize(node);

        for (int i = 0; i < 5; i++) {
            Node simplified = simplifyPass(node);
            if (simplified.equals(node)) break;
            node = canonicalize(simplified);
        }

        if (node instanceof Node.Op op) {
            if (isXor(op, 0) && op.right instanceof Node.Constant) {
                return op.right;
            }
            if (isAnd(op, -1) && op.right instanceof Node.Constant c && c.value() == -1) {
                return op.left;
            }
        }

        return tryTruthTableSimplify(node);
    }

    private static Node canonicalize(Node node) {
        if (node instanceof Node.Op op) {
            Node left = canonicalize(op.left);
            Node right = canonicalize(op.right);

            if (op.kind == AND || op.kind == OR || op.kind == XOR || op.kind == ADD || op.kind == MUL) {
                if (isGreater(left, right)) {
                    return new Node.Op(op.kind, right, left);
                }
            }

            return new Node.Op(op.kind, left, right);
        }
        return node;
    }

    private static boolean isGreater(Node a, Node b) {
        if (a instanceof Node.Constant ac && b instanceof Node.Constant bc) {
            return ac.value() > bc.value();
        }
        if (a instanceof Node.Constant) return true;
        if (b instanceof Node.Constant) return false;
        if (a instanceof Node.Variable av && b instanceof Node.Variable bv) {
            return av.id() > bv.id();
        }
        if (a instanceof Node.Variable) return true;
        return a.toString().compareTo(b.toString()) > 0;
    }

    private static Node simplifyPass(Node node) {
        if (node instanceof Node.Op op) {

            if (op.left instanceof Node.Op ol && ol.kind == NOT) {
                Node inner = ol.left;
                return switch (op.kind) {
                    case AND -> {
                        if (op.right instanceof Node.Constant c && c.value() == -1) yield new Node.Op(NOT, inner, null);
                        yield node;
                    }
                    default -> node;
                };
            }

            if (op.left instanceof Node.Constant cl && op.right instanceof Node.Constant cr) {
                int l = cl.value();
                int r = cr.value();
                return new Node.Constant(switch (op.kind) {
                    case AND -> l & r;
                    case OR  -> l | r;
                    case XOR -> l ^ r;
                    case ADD -> l + r;
                    case SUB -> l - r;
                    case MUL -> l * r;
                    case SHL -> l << r;
                    case SHR -> l >> r;
                    case USHR -> l >>> r;
                    default -> 0;
                });
            }

            if (op.left instanceof Node.Op inner) {
                Node simplified = simplifyPattern(inner, op.right, op.kind);
                if (simplified != null) return simplified;
            }
            if (op.right instanceof Node.Op inner) {
                Node simplified = simplifyPattern(inner, op.left, op.kind);
                if (simplified != null) return simplified;
            }

            if (op.kind == XOR && op.left.equals(op.right)) {
                return new Node.Constant(0);
            }
            if (isAnd(op, 0)) {
                return new Node.Constant(0);
            }
            if (isOr(op, -1)) {
                return new Node.Constant(-1);
            }
            if (op.kind == SUB && op.left.equals(op.right)) {
                return new Node.Constant(0);
            }

            if (op.kind == AND && op.right instanceof Node.Constant c && c.value() == 1) {
                if (isSingleBit(op.left)) return op.left;
            }
        }
        return node;
    }

    private static Node simplifyPattern(Node.Op inner, Node other, int outerKind) {
        int ik = inner.kind;
        Node a = inner.left;
        Node b = inner.right;

        if (outerKind == AND && ik == OR) {
            Node notA = new Node.Op(NOT, a, null);
            if (notA.equals(b)) return makeXor(a, other);
            Node notOther = new Node.Op(NOT, other, null);
            if (b.equals(notOther)) return new Node.Op(AND, new Node.Op(NOT, a, null), notOther);
        }

        if (outerKind == AND && ik == XOR && isNot(b, a)) {
            return new Node.Constant(0);
        }
        if (outerKind == OR && ik == XOR && isNot(b, a)) {
            return new Node.Constant(-1);
        }

        if (outerKind == XOR && ik == AND && b instanceof Node.Constant c && c.value() == -1) {
            return new Node.Op(AND, a, new Node.Op(NOT, other, null));
        }

        if (outerKind == ADD && ik == NEG) {
            return new Node.Op(SUB, other, a);
        }

        if (outerKind == OR && ik == NEG) {
            return new Node.Constant(-1);
        }
        if (outerKind == AND && ik == NOT) {
            return mbaSimplify(other, a);
        }

        if (outerKind == AND && ik == OR && isNot(b, a) && isNot(other, a)) {
            return new Node.Constant(0);
        }

        if (outerKind == AND && ik == OR && b instanceof Node.Op bop && bop.kind == NOT) {
            Node bn = bop.left;
            if (bn instanceof Node.Variable && other instanceof Node.Op oop && oop.kind == NOT && oop.left.equals(bn) && a instanceof Node.Variable) {
                return new Node.Constant(0);
            }
        }

        if (outerKind == AND && ik == AND) {
            if (a instanceof Node.Op ao && ao.kind == NOT) {
                if (b instanceof Node.Variable bv && other instanceof Node.Variable ov && bv.equals(ov) && ao.left instanceof Node.Variable avl && !avl.equals(bv)) {
                    return new Node.Op(AND, new Node.Op(NOT, ao.left, null), bv);
                }
            }
        }

        if (outerKind == XOR && ik == ADD) {
            if (a.equals(other)) return b;
            if (b.equals(other)) return a;
        }

        if (outerKind == XOR && ik == SUB) {
            if (a instanceof Node.Variable va && other instanceof Node.Variable vo && va.equals(vo)) return b;
            if (isNot(a, b) && other instanceof Node.Variable) return new Node.Op(ADD, a, new Node.Op(XOR, b, other));
        }

        if (outerKind == XOR && ik == AND && a instanceof Node.Constant ca && ca.value() == -1) {
            return new Node.Op(NOT, new Node.Op(XOR, b, other));
        }

        if (outerKind == OR && ik == AND && a instanceof Node.Op ano && ano.kind == NOT && b instanceof Node.Op bno && bno.kind == NOT) {
            return new Node.Op(NOT, new Node.Op(AND, ano.left, bno.left));
        }

        if (outerKind == AND && ik == OR) {
            if (a instanceof Node.Op aor && aor.kind == NOT && b instanceof Node.Op bor && bor.kind == NOT) {
                Node x = aor.left;
                Node y = bor.left;
                if (x.equals(other)) return new Node.Op(AND, new Node.Op(NOT, y), x);
                if (y.equals(other)) return new Node.Op(AND, new Node.Op(NOT, x), y);
            }
        }

        return null;
    }

    private static Node mbaSimplify(Node a, Node notB) {
        if (a instanceof Node.Op ao && ao.kind == AND) {
            if (ao.left.equals(notB)) return new Node.Op(AND, notB, new Node.Op(NOT, ao.right));
            if (ao.right.equals(notB)) return new Node.Op(AND, notB, new Node.Op(NOT, ao.left));
        }
        if (a instanceof Node.Op ao && ao.kind == XOR) {
            if (ao.left.equals(notB)) return new Node.Op(AND, new Node.Op(NOT, ao.right), notB);
            if (ao.right.equals(notB)) return new Node.Op(AND, new Node.Op(NOT, ao.left), notB);
        }
        if (a.equals(notB)) return new Node.Constant(0);
        return new Node.Op(AND, notB, new Node.Op(NOT, a));
    }

    private static Node makeXor(Node a, Node b) {
        return new Node.Op(XOR, a, b);
    }

    private static boolean isNot(Node node, Node target) {
        return node instanceof Node.Op o && o.kind == NOT && o.left.equals(target);
    }

    private static boolean isAnd(Node.Op op, int val) {
        return op.kind == AND && op.right instanceof Node.Constant c && c.value() == val;
    }

    private static boolean isOr(Node.Op op, int val) {
        return op.kind == OR && op.right instanceof Node.Constant c && c.value() == val && val == -1;
    }

    private static boolean isXor(Node.Op op, int val) {
        return op.kind == XOR && op.right instanceof Node.Constant c && c.value() == val;
    }

    private static boolean isSingleBit(Node node) {
        if (node instanceof Node.Constant c) {
            int v = c.value();
            return v != 0 && (v & (v - 1)) == 0;
        }
        return false;
    }

    private static Node tryTruthTableSimplify(Node node) {
        Set<Node.Variable> vars = new LinkedHashSet<>();
        collectVars(node, vars);
        if (vars.size() < 2 || vars.size() > 4) return node;

        List<Node.Variable> varList = new ArrayList<>(vars);
        int combos = 1 << varList.size();

        int[] results = new int[combos];
        boolean allBit = true;
        for (int i = 0; i < combos; i++) {
            Map<Node.Variable, Integer> env = new HashMap<>();
            for (int j = 0; j < varList.size(); j++) {
                env.put(varList.get(j), (i >> j) & 1);
            }
            int val = evaluateBits(node, env);
            if (val != 0 && val != 1) { allBit = false; break; }
            results[i] = val;
        }

        if (!allBit) return node;

        if (varList.size() == 2) {
            Node v0 = varList.get(0);
            Node v1 = varList.get(1);
            for (int pattern = 0; pattern < 16; pattern++) {
                boolean match = true;
                for (int i = 0; i < 4; i++) {
                    int a = (i >> 1) & 1;
                    int b = i & 1;
                    int expected = (pattern >> ((a << 1) | b)) & 1;
                    if (results[i] != expected) { match = false; break; }
                }
                if (match) {
                    return buildCanonical(v0, v1, pattern);
                }
            }
        }

        if (varList.size() == 3) {
            Node v0 = varList.get(0);
            Node v1 = varList.get(1);
            Node v2 = varList.get(2);
            for (int used : new int[]{0, 1}) {
                Node a = used == 0 ? v0 : v1;
                Node b = used == 0 ? v1 : v2;
                if (isIndependent(results, varList.size(), varList.indexOf(a), varList.indexOf(b))) {
                    for (int pattern = 0; pattern < 16; pattern++) {
                        boolean match = true;
                        for (int i = 0; i < 8; i++) {
                            int ai = (i >> varList.indexOf(a)) & 1;
                            int bi = (i >> varList.indexOf(b)) & 1;
                            int expected = (pattern >> ((ai << 1) | bi)) & 1;
                            if (results[i] != expected) { match = false; break; }
                        }
                        if (match) return buildCanonical(a, b, pattern);
                    }
                }
            }
        }

        return node;
    }

    private static boolean isIndependent(int[] results, int numVars, int aIdx, int bIdx) {
        int mask = (1 << aIdx) | (1 << bIdx);
        for (int i = 0; i < results.length; i++) {
            for (int j = i + 1; j < results.length; j++) {
                if ((i & mask) == (j & mask) && results[i] != results[j]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Node buildCanonical(Node a, Node b, int pattern) {
        return switch (pattern) {
            case 0 -> new Node.Constant(0);
            case 1 -> new Node.Op(NOT, new Node.Op(OR, a, b));
            case 2 -> new Node.Op(AND, new Node.Op(NOT, a), b);
            case 3 -> new Node.Op(NOT, a);
            case 4 -> new Node.Op(AND, a, new Node.Op(NOT, b));
            case 5 -> new Node.Op(NOT, b);
            case 6 -> new Node.Op(XOR, a, b);
            case 7 -> new Node.Op(NOT, new Node.Op(AND, a, b));
            case 8 -> new Node.Op(AND, a, b);
            case 9 -> new Node.Op(NOT, new Node.Op(XOR, a, b));
            case 10 -> b;
            case 11 -> new Node.Op(OR, new Node.Op(NOT, a), b);
            case 12 -> a;
            case 13 -> new Node.Op(OR, a, new Node.Op(NOT, b));
            case 14 -> new Node.Op(OR, a, b);
            case 15 -> new Node.Constant(-1);
            default -> new Node.Constant(0);
        };
    }

    private static int evaluateBits(Node node, Map<Node.Variable, Integer> env) {
        return switch (node) {
            case Node.Constant c -> c.value() & 1;
            case Node.Variable v -> env.getOrDefault(v, 0);
            case Node.Op op -> {
                int l = evaluateBits(op.left, env);
                int r = evaluateBits(op.right, env);
                yield switch (op.kind) {
                    case NOT -> (~l) & 1;
                    case NEG -> (-l) & 1;
                    case AND -> l & r;
                    case OR  -> l | r;
                    case XOR -> l ^ r;
                    case ADD -> (l + r) & 1;
                    case SUB -> (l - r) & 1;
                    case MUL -> (l * r) & 1;
                    default -> 0;
                };
            }
        };
    }

    private static void collectVars(Node node, Set<Node.Variable> out) {
        switch (node) {
            case Node.Variable v -> out.add(v);
            case Node.Op op -> {
                if (op.left != null) collectVars(op.left, out);
                if (op.right != null) collectVars(op.right, out);
            }
            default -> {}
        }
    }

    public static Node parseMbaExpr(String expr) {
        return null;
    }

    public static String toJavaExpr(Node node) {
        if (node == null) return "0";
        return switch (node) {
            case Node.Constant c -> String.valueOf(c.value());
            case Node.Variable v -> v.toString();
            case Node.Op op -> {
                String l = toJavaExpr(op.left);
                String r = toJavaExpr(op.right);
                yield switch (op.kind) {
                    case NOT -> "(~" + l + ")";
                    case NEG -> "(-" + l + ")";
                    case AND -> "(" + l + " & " + r + ")";
                    case OR  -> "(" + l + " | " + r + ")";
                    case XOR -> "(" + l + " ^ " + r + ")";
                    case ADD -> "(" + l + " + " + r + ")";
                    case SUB -> "(" + l + " - " + r + ")";
                    case MUL -> "(" + l + " * " + r + ")";
                    case SHL -> "(" + l + " << " + r + ")";
                    case SHR -> "(" + l + " >> " + r + ")";
                    case USHR -> "(" + l + " >>> " + r + ")";
                    default -> "(?" + l + "?" + r + "?)";
                };
            }
        };
    }

    public static int evaluate(Node node, Map<Integer, Integer> env) {
        return switch (node) {
            case Node.Constant c -> c.value();
            case Node.Variable v -> env.getOrDefault(v.id(), 0);
            case Node.Op op -> {
                int l = evaluate(op.left, env);
                int r = evaluate(op.right, env);
                yield switch (op.kind) {
                    case NOT -> ~l;
                    case NEG -> -l;
                    case AND -> l & r;
                    case OR  -> l | r;
                    case XOR -> l ^ r;
                    case ADD -> l + r;
                    case SUB -> l - r;
                    case MUL -> l * r;
                    case SHL -> l << r;
                    case SHR -> l >> r;
                    case USHR -> l >>> r;
                    default -> 0;
                };
            }
        };
    }
}
