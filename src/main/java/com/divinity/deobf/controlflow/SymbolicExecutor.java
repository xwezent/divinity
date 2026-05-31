package com.divinity.deobf.controlflow;

import com.divinity.cfg.BasicBlock;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import java.util.*;

public final class SymbolicExecutor {

    public SymbolicState execute(BasicBlock block, int dispatcherVar) {
        SymbolicState state = new SymbolicState();
        Deque<SymbolicValue> stack = new ArrayDeque<>();
        Map<Integer, SymbolicValue> locals = new LinkedHashMap<>();

        for (Instruction inst : block.instructions) {
            executeInstruction(inst, stack, locals, state, dispatcherVar);
        }

        return state;
    }

    private void executeInstruction(Instruction inst, Deque<SymbolicValue> stack,
                                     Map<Integer, SymbolicValue> locals,
                                     SymbolicState state, int dispatcherVar) {
        Opcode op = inst.opcode;

        switch (op) {
            case ICONST_M1 -> stack.push(new SymbolicValue.Constant(-1));
            case ICONST_0 -> stack.push(new SymbolicValue.Constant(0));
            case ICONST_1 -> stack.push(new SymbolicValue.Constant(1));
            case ICONST_2 -> stack.push(new SymbolicValue.Constant(2));
            case ICONST_3 -> stack.push(new SymbolicValue.Constant(3));
            case ICONST_4 -> stack.push(new SymbolicValue.Constant(4));
            case ICONST_5 -> stack.push(new SymbolicValue.Constant(5));
            case BIPUSH -> stack.push(new SymbolicValue.Constant((byte) inst.operands[0]));
            case SIPUSH -> stack.push(new SymbolicValue.Constant(
                    (short) ((inst.operands[0] << 8) | inst.operands[1])));
            case LDC, LDC_W -> {
                int idx = op == Opcode.LDC ? (inst.operands[0] & 0xFF) :
                        ((inst.operands[0] << 8) | inst.operands[1]);
                stack.push(new SymbolicValue.Unknown());
            }

            case ILOAD, ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3 -> {
                int varIdx = getVarIndex(inst);
                SymbolicValue val = locals.getOrDefault(varIdx, new SymbolicValue.Unknown());
                stack.push(val);
            }

            case ISTORE, ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3 -> {
                int varIdx = getVarIndex(inst);
                SymbolicValue val = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                locals.put(varIdx, val);

                if (varIdx == dispatcherVar && val instanceof SymbolicValue.Constant c) {
                    state.setDispatcherValue(c.value());
                }
            }

            case IADD -> {
                SymbolicValue right = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                SymbolicValue left = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                if (left instanceof SymbolicValue.Constant lc && right instanceof SymbolicValue.Constant rc) {
                    stack.push(new SymbolicValue.Constant(lc.value() + rc.value()));
                } else {
                    stack.push(new SymbolicValue.BinaryOp("+", left, right));
                }
            }
            case ISUB -> {
                SymbolicValue right = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                SymbolicValue left = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                if (left instanceof SymbolicValue.Constant lc && right instanceof SymbolicValue.Constant rc) {
                    stack.push(new SymbolicValue.Constant(lc.value() - rc.value()));
                } else {
                    stack.push(new SymbolicValue.BinaryOp("-", left, right));
                }
            }
            case IMUL -> {
                SymbolicValue right = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                SymbolicValue left = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                if (left instanceof SymbolicValue.Constant lc && right instanceof SymbolicValue.Constant rc) {
                    stack.push(new SymbolicValue.Constant(lc.value() * rc.value()));
                } else {
                    stack.push(new SymbolicValue.BinaryOp("*", left, right));
                }
            }
            case IDIV -> {
                SymbolicValue right = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                SymbolicValue left = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                if (left instanceof SymbolicValue.Constant lc && right instanceof SymbolicValue.Constant rc && rc.value() != 0) {
                    stack.push(new SymbolicValue.Constant(lc.value() / rc.value()));
                } else {
                    stack.push(new SymbolicValue.BinaryOp("/", left, right));
                }
            }
            case IREM -> {
                SymbolicValue right = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                SymbolicValue left = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                if (left instanceof SymbolicValue.Constant lc && right instanceof SymbolicValue.Constant rc && rc.value() != 0) {
                    stack.push(new SymbolicValue.Constant(lc.value() % rc.value()));
                } else {
                    stack.push(new SymbolicValue.BinaryOp("%", left, right));
                }
            }
            case IAND -> {
                SymbolicValue right = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                SymbolicValue left = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                if (left instanceof SymbolicValue.Constant lc && right instanceof SymbolicValue.Constant rc) {
                    stack.push(new SymbolicValue.Constant(lc.value() & rc.value()));
                } else {
                    stack.push(new SymbolicValue.BinaryOp("&", left, right));
                }
            }
            case IOR -> {
                SymbolicValue right = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                SymbolicValue left = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                if (left instanceof SymbolicValue.Constant lc && right instanceof SymbolicValue.Constant rc) {
                    stack.push(new SymbolicValue.Constant(lc.value() | rc.value()));
                } else {
                    stack.push(new SymbolicValue.BinaryOp("|", left, right));
                }
            }
            case IXOR -> {
                SymbolicValue right = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                SymbolicValue left = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                if (left instanceof SymbolicValue.Constant lc && right instanceof SymbolicValue.Constant rc) {
                    stack.push(new SymbolicValue.Constant(lc.value() ^ rc.value()));
                } else {
                    stack.push(new SymbolicValue.BinaryOp("^", left, right));
                }
            }
            case ISHL -> {
                SymbolicValue right = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                SymbolicValue left = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                if (left instanceof SymbolicValue.Constant lc && right instanceof SymbolicValue.Constant rc) {
                    stack.push(new SymbolicValue.Constant(lc.value() << rc.value()));
                } else {
                    stack.push(new SymbolicValue.BinaryOp("<<", left, right));
                }
            }
            case ISHR -> {
                SymbolicValue right = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                SymbolicValue left = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                if (left instanceof SymbolicValue.Constant lc && right instanceof SymbolicValue.Constant rc) {
                    stack.push(new SymbolicValue.Constant(lc.value() >> rc.value()));
                } else {
                    stack.push(new SymbolicValue.BinaryOp(">>", left, right));
                }
            }
            case IUSHR -> {
                SymbolicValue right = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                SymbolicValue left = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                if (left instanceof SymbolicValue.Constant lc && right instanceof SymbolicValue.Constant rc) {
                    stack.push(new SymbolicValue.Constant(lc.value() >>> rc.value()));
                } else {
                    stack.push(new SymbolicValue.BinaryOp(">>>", left, right));
                }
            }
            case INEG -> {
                SymbolicValue val = stack.isEmpty() ? new SymbolicValue.Unknown() : stack.pop();
                if (val instanceof SymbolicValue.Constant c) {
                    stack.push(new SymbolicValue.Constant(-c.value()));
                } else {
                    stack.push(new SymbolicValue.UnaryOp("-", val));
                }
            }

            case DUP -> {
                if (!stack.isEmpty()) {
                    stack.push(stack.peek());
                }
            }
            case POP -> {
                if (!stack.isEmpty()) stack.pop();
            }

            case GOTO, GOTO_W -> {}
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> {
                if (!stack.isEmpty()) stack.pop();
            }
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                if (!stack.isEmpty()) stack.pop();
                if (!stack.isEmpty()) stack.pop();
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
        record Constant(int value) implements SymbolicValue {}
        record Variable(int index) implements SymbolicValue {}
        record BinaryOp(String operator, SymbolicValue left, SymbolicValue right) implements SymbolicValue {}
        record UnaryOp(String operator, SymbolicValue operand) implements SymbolicValue {}
        record Unknown() implements SymbolicValue {}
    }

    public static final class SymbolicState {
        private Integer dispatcherValue;

        public Integer getDispatcherValue() {
            return dispatcherValue;
        }

        public void setDispatcherValue(Integer value) {
            this.dispatcherValue = value;
        }
    }
}
