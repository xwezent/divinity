package com.divinity.deobf.mba;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class Z3MbaSolver {

    private static Boolean z3Available = null;
    private static final int TIMEOUT_MS = 5000;

    public static boolean isAvailable() {
        if (z3Available != null) return z3Available;

        try {
            Process process = new ProcessBuilder("z3", "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            z3Available = finished && process.exitValue() == 0;
        } catch (Exception e) {
            z3Available = false;
        }

        return z3Available;
    }

    public static MbaSolver.Node simplify(MbaSolver.Node node) {
        if (!isAvailable()) return null;

        try {
            String smtLib = toSmtLib(node);
            String result = runZ3(smtLib);
            return parseZ3Result(result, node);
        } catch (Exception e) {
            return null;
        }
    }

    private static String toSmtLib(MbaSolver.Node node) {
        StringBuilder sb = new StringBuilder();
        sb.append("(set-logic QF_BV)\n");

        Set<MbaSolver.Node.Variable> vars = new LinkedHashSet<>();
        collectVariables(node, vars);

        for (MbaSolver.Node.Variable var : vars) {
            sb.append("(declare-const ").append(var.toString()).append(" (_ BitVec 32))\n");
        }

        sb.append("(simplify ");
        sb.append(toSmtLibExpr(node));
        sb.append(")\n");

        return sb.toString();
    }

    private static void collectVariables(MbaSolver.Node node, Set<MbaSolver.Node.Variable> vars) {
        switch (node) {
            case MbaSolver.Node.Variable v -> vars.add(v);
            case MbaSolver.Node.Op op -> {
                if (op.left() != null) collectVariables(op.left(), vars);
                if (op.right() != null) collectVariables(op.right(), vars);
            }
            default -> {}
        }
    }

    private static String toSmtLibExpr(MbaSolver.Node node) {
        return switch (node) {
            case MbaSolver.Node.Constant c -> {
                if (c.value() < 0) {
                    yield "(bvneg #x" + String.format("%08x", -c.value()) + ")";
                }
                yield "#x" + String.format("%08x", c.value());
            }
            case MbaSolver.Node.Variable v -> v.toString();
            case MbaSolver.Node.Op op -> {
                String left = toSmtLibExpr(op.left());
                String right = op.right() != null ? toSmtLibExpr(op.right()) : null;

                yield switch (op.kind()) {
                    case MbaSolver.NOT -> "(bvnot " + left + ")";
                    case MbaSolver.NEG -> "(bvneg " + left + ")";
                    case MbaSolver.AND -> "(bvand " + left + " " + right + ")";
                    case MbaSolver.OR -> "(bvor " + left + " " + right + ")";
                    case MbaSolver.XOR -> "(bvxor " + left + " " + right + ")";
                    case MbaSolver.ADD -> "(bvadd " + left + " " + right + ")";
                    case MbaSolver.SUB -> "(bvsub " + left + " " + right + ")";
                    case MbaSolver.MUL -> "(bvmul " + left + " " + right + ")";
                    case MbaSolver.SHL -> "(bvshl " + left + " " + right + ")";
                    case MbaSolver.SHR -> "(bvashr " + left + " " + right + ")";
                    case MbaSolver.USHR -> "(bvlshr " + left + " " + right + ")";
                    default -> left;
                };
            }
        };
    }

    private static String runZ3(String smtLib) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("z3", "-in", "-smt2")
                .redirectErrorStream(true)
                .start();

        try (OutputStream os = process.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os))) {
            writer.write(smtLib);
            writer.flush();
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Z3 timeout");
        }

        return output.toString();
    }

    private static MbaSolver.Node parseZ3Result(String result, MbaSolver.Node original) {
        result = result.trim();

        if (result.startsWith("#x")) {
            try {
                int value = Integer.parseUnsignedInt(result.substring(2), 16);
                return new MbaSolver.Node.Constant(value);
            } catch (NumberFormatException e) {
                return original;
            }
        }

        if (result.startsWith("(bvneg #x")) {
            try {
                int endIdx = result.indexOf(')');
                String hex = result.substring(9, endIdx);
                int value = -Integer.parseUnsignedInt(hex, 16);
                return new MbaSolver.Node.Constant(value);
            } catch (Exception e) {
                return original;
            }
        }

        if (result.startsWith("v") && result.matches("v\\d+")) {
            try {
                int id = Integer.parseInt(result.substring(1));
                return new MbaSolver.Node.Variable(id);
            } catch (NumberFormatException e) {
                return original;
            }
        }

        return original;
    }

    public static MbaSolver.Node solveEquivalence(MbaSolver.Node expr1, MbaSolver.Node expr2) {
        if (!isAvailable()) return null;

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("(set-logic QF_BV)\n");

            Set<MbaSolver.Node.Variable> vars = new LinkedHashSet<>();
            collectVariables(expr1, vars);
            collectVariables(expr2, vars);

            for (MbaSolver.Node.Variable var : vars) {
                sb.append("(declare-const ").append(var.toString()).append(" (_ BitVec 32))\n");
            }

            sb.append("(assert (not (= ");
            sb.append(toSmtLibExpr(expr1));
            sb.append(" ");
            sb.append(toSmtLibExpr(expr2));
            sb.append(")))\n");
            sb.append("(check-sat)\n");

            String result = runZ3(sb.toString());

            if (result.trim().equals("unsat")) {
                return expr2;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
