package com.divinity.deobf.lambda;

import com.divinity.cfg.ControlFlowGraph;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import com.divinity.cfg.BasicBlock;
import com.divinity.classfile.ClassFileParser;
import java.util.*;

public final class LambdaReconstructor {

    private final ControlFlowGraph cfg;
    private final ClassFileParser classParser;
    private final Map<Integer, LambdaInfo> lambdas;

    public LambdaReconstructor(ControlFlowGraph cfg, ClassFileParser classParser) {
        this.cfg = cfg;
        this.classParser = classParser;
        this.lambdas = new LinkedHashMap<>();
    }

    public void reconstruct() {
        for (BasicBlock block : cfg.getBlocks()) {
            analyzeBlock(block);
        }
    }

    private void analyzeBlock(BasicBlock block) {
        List<Instruction> instructions = block.instructions;

        for (int i = 0; i < instructions.size(); i++) {
            Instruction inst = instructions.get(i);

            if (inst.opcode == Opcode.INVOKEDYNAMIC) {
                LambdaInfo lambda = analyzeLambda(instructions, i);
                if (lambda != null) {
                    lambdas.put(i, lambda);
                }
            }
        }
    }

    private LambdaInfo analyzeLambda(List<Instruction> instructions, int index) {
        Instruction inst = instructions.get(index);

        int bsmIndex = (inst.operands[0] << 8) | inst.operands[1];

        String functionalInterface = inferFunctionalInterface(inst);
        String methodReference = inferMethodReference(instructions, index);
        LambdaType type = inferLambdaType(functionalInterface);

        return new LambdaInfo(type, functionalInterface, methodReference, List.of());
    }

    private String inferFunctionalInterface(Instruction inst) {
        return "java.util.function.Function";
    }

    private String inferMethodReference(List<Instruction> instructions, int index) {
        for (int i = index - 1; i >= Math.max(0, index - 5); i--) {
            Instruction prev = instructions.get(i);
            if (prev.opcode == Opcode.ALOAD || prev.opcode == Opcode.ALOAD_0) {
                return "this::method";
            }
        }
        return null;
    }

    private LambdaType inferLambdaType(String functionalInterface) {
        if (functionalInterface == null) return LambdaType.UNKNOWN;

        return switch (functionalInterface) {
            case "java.util.function.Function" -> LambdaType.FUNCTION;
            case "java.util.function.Consumer" -> LambdaType.CONSUMER;
            case "java.util.function.Supplier" -> LambdaType.SUPPLIER;
            case "java.util.function.Predicate" -> LambdaType.PREDICATE;
            case "java.lang.Runnable" -> LambdaType.RUNNABLE;
            default -> LambdaType.CUSTOM;
        };
    }

    public Map<Integer, LambdaInfo> getLambdas() {
        return lambdas;
    }

    public enum LambdaType {
        FUNCTION,
        CONSUMER,
        SUPPLIER,
        PREDICATE,
        RUNNABLE,
        CUSTOM,
        UNKNOWN
    }

    public record LambdaInfo(LambdaType type, String functionalInterface,
                             String methodReference, List<String> capturedVariables) {
        @Override
        public String toString() {
            if (methodReference != null) {
                return methodReference;
            }
            return "(" + String.join(", ", capturedVariables) + ") -> { ... }";
        }

        public String toJavaCode() {
            if (methodReference != null && !methodReference.isEmpty()) {
                return methodReference;
            }

            String params = capturedVariables.isEmpty() ? "" :
                String.join(", ", capturedVariables);

            return switch (type) {
                case FUNCTION -> "(" + params + ") -> { /* function body */ }";
                case CONSUMER -> "(" + params + ") -> { /* consumer body */ }";
                case SUPPLIER -> "() -> { /* supplier body */ }";
                case PREDICATE -> "(" + params + ") -> { /* predicate body */ }";
                case RUNNABLE -> "() -> { /* runnable body */ }";
                default -> "(" + params + ") -> { /* lambda body */ }";
            };
        }
    }
}
