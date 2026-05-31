package com.divinity.symbolic;

import com.divinity.cfg.BasicBlock;
import com.divinity.cfg.ControlFlowGraph;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import java.util.*;

public final class SymbolicExecutionEngine {

    private final ControlFlowGraph cfg;
    private final Map<Integer, SymbolicValue> symbolicState;

    public SymbolicExecutionEngine(ControlFlowGraph cfg) {
        this.cfg = cfg;
        this.symbolicState = new LinkedHashMap<>();
    }

    public ExecutionResult execute(BasicBlock startBlock, int targetVariable) {
        Map<BasicBlock, Set<SymbolicState>> visitedStates = new LinkedHashMap<>();
        Queue<WorkItem> workQueue = new ArrayDeque<>();

        SymbolicState initialState = new SymbolicState();
        workQueue.add(new WorkItem(startBlock, initialState));

        while (!workQueue.isEmpty()) {
            WorkItem item = workQueue.poll();
            BasicBlock block = item.block;
            SymbolicState state = item.state;

            Set<SymbolicState> visited = visitedStates.computeIfAbsent(block, k -> new LinkedHashSet<>());
            if (visited.contains(state)) continue;
            visited.add(state);

            SymbolicState resultState = executeBlock(block, state);

            for (BasicBlock successor : block.successors) {
                workQueue.add(new WorkItem(successor, resultState.copy()));
            }
        }

        return new ExecutionResult(visitedStates, symbolicState);
    }

    private SymbolicState executeBlock(BasicBlock block, SymbolicState state) {
        Deque<SymbolicValue> stack = new ArrayDeque<>(state.stack);
        Map<Integer, SymbolicValue> locals = new LinkedHashMap<>(state.locals);

        for (Instruction inst : block.instructions) {
            executeInstruction(inst, stack, locals);
        }

        return new SymbolicState(stack, locals);
    }

    private void executeInstruction(Instruction inst, Deque<SymbolicValue> stack,
                                    Map<Integer, SymbolicValue> locals) {
        switch (inst.opcode) {
            case ICONST_M1 -> stack.push(new SymbolicValue.Constant(-1));
            case ICONST_0 -> stack.push(new SymbolicValue.Constant(0));
            case ICONST_1 -> stack.push(new SymbolicValue.Constant(1));
            case ICONST_2 -> stack.push(new SymbolicValue.Constant(2));
            case ICONST_3 -> stack.push(new SymbolicValue.Constant(3));
            case ICONST_4 -> stack.push(new SymbolicValue.Constant(4));
            case ICONST_5 -> stack.push(new SymbolicValue.Constant(5));

            case BIPUSH -> stack.push(new SymbolicValue.Constant(inst.operands[0]));
            case SIPUSH -> stack.push(new SymbolicValue.Constant(
                (inst.operands[0] << 8) | inst.operands[1]));

            case ILOAD, ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3 -> {
                int varIdx = getVarIndex(inst);
                SymbolicValue value = locals.getOrDefault(varIdx, new SymbolicValue.Variable(varIdx));
                stack.push(value);
            }

            case ISTORE, ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3 -> {
                int varIdx = getVarIndex(inst);
                if (!stack.isEmpty()) {
                    locals.put(varIdx, stack.pop());
                }
            }

            case IADD -> {
                if (stack.size() >= 2) {
                    SymbolicValue right = stack.pop();
                    SymbolicValue left = stack.pop();
                    stack.push(new SymbolicValue.BinaryOp("+", left, right));
                }
            }

            case ISUB -> {
                if (stack.size() >= 2) {
                    SymbolicValue right = stack.pop();
                    SymbolicValue left = stack.pop();
                    stack.push(new SymbolicValue.BinaryOp("-", left, right));
                }
            }

            case IMUL -> {
                if (stack.size() >= 2) {
                    SymbolicValue right = stack.pop();
                    SymbolicValue left = stack.pop();
                    stack.push(new SymbolicValue.BinaryOp("*", left, right));
                }
            }

            case IAND -> {
                if (stack.size() >= 2) {
                    SymbolicValue right = stack.pop();
                    SymbolicValue left = stack.pop();
                    stack.push(new SymbolicValue.BinaryOp("&", left, right));
                }
            }

            case IOR -> {
                if (stack.size() >= 2) {
                    SymbolicValue right = stack.pop();
                    SymbolicValue left = stack.pop();
                    stack.push(new SymbolicValue.BinaryOp("|", left, right));
                }
            }

            case IXOR -> {
                if (stack.size() >= 2) {
                    SymbolicValue right = stack.pop();
                    SymbolicValue left = stack.pop();
                    stack.push(new SymbolicValue.BinaryOp("^", left, right));
                }
            }

            default -> {}
        }
    }

    private int getVarIndex(Instruction inst) {
        return switch (inst.opcode) {
            case ILOAD, ISTORE -> inst.operands.length > 0 ? inst.operands[0] & 0xFF : 0;
            case ILOAD_0, ISTORE_0 -> 0;
            case ILOAD_1, ISTORE_1 -> 1;
            case ILOAD_2, ISTORE_2 -> 2;
            case ILOAD_3, ISTORE_3 -> 3;
            default -> 0;
        };
    }

    public sealed interface SymbolicValue {
        record Constant(int value) implements SymbolicValue {
            @Override
            public String toString() { return String.valueOf(value); }
        }

        record Variable(int index) implements SymbolicValue {
            @Override
            public String toString() { return "var" + index; }
        }

        record BinaryOp(String operator, SymbolicValue left, SymbolicValue right) implements SymbolicValue {
            @Override
            public String toString() { return "(" + left + " " + operator + " " + right + ")"; }
        }

        record UnaryOp(String operator, SymbolicValue operand) implements SymbolicValue {
            @Override
            public String toString() { return operator + operand; }
        }
    }

    public static class SymbolicState {
        final Deque<SymbolicValue> stack;
        final Map<Integer, SymbolicValue> locals;

        public SymbolicState() {
            this.stack = new ArrayDeque<>();
            this.locals = new LinkedHashMap<>();
        }

        public SymbolicState(Deque<SymbolicValue> stack, Map<Integer, SymbolicValue> locals) {
            this.stack = stack;
            this.locals = locals;
        }

        public SymbolicState copy() {
            return new SymbolicState(new ArrayDeque<>(stack), new LinkedHashMap<>(locals));
        }

        public Integer getDispatcherValue() {
            if (stack.isEmpty()) return null;
            SymbolicValue top = stack.peek();
            if (top instanceof SymbolicValue.Constant c) {
                return c.value();
            }
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SymbolicState that)) return false;
            return stack.equals(that.stack) && locals.equals(that.locals);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stack, locals);
        }
    }

    private record WorkItem(BasicBlock block, SymbolicState state) {}

    public record ExecutionResult(
        Map<BasicBlock, Set<SymbolicState>> visitedStates,
        Map<Integer, SymbolicValue> finalState
    ) {}
}
