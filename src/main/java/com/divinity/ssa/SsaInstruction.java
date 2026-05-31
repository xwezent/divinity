package com.divinity.ssa;

import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import java.util.*;

public sealed interface SsaInstruction {

    SsaVariable result();
    List<SsaVariable> operands();
    void replaceOperand(SsaVariable old, SsaVariable newVar);

    record Assign(SsaVariable result, SsaVariable source) implements SsaInstruction {
        @Override public List<SsaVariable> operands() { return List.of(source); }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record Constant(SsaVariable result, Object value) implements SsaInstruction {
        @Override public List<SsaVariable> operands() { return List.of(); }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record BinaryOp(SsaVariable result, String operator, SsaVariable left, SsaVariable right) implements SsaInstruction {
        @Override public List<SsaVariable> operands() { return List.of(left, right); }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record UnaryOp(SsaVariable result, String operator, SsaVariable operand) implements SsaInstruction {
        @Override public List<SsaVariable> operands() { return List.of(operand); }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record Phi(SsaVariable result, Map<Integer, SsaVariable> incoming) implements SsaInstruction {
        @Override
        public List<SsaVariable> operands() {
            return new ArrayList<>(incoming.values());
        }

        @Override
        public void replaceOperand(SsaVariable old, SsaVariable newVar) {
            for (Map.Entry<Integer, SsaVariable> entry : incoming.entrySet()) {
                if (entry.getValue().equals(old)) {
                    incoming.put(entry.getKey(), newVar);
                }
            }
        }

        public void addIncoming(int blockId, SsaVariable var) {
            incoming.put(blockId, var);
        }
    }

    record Call(SsaVariable result, String owner, String name, String descriptor,
                List<SsaVariable> arguments, boolean isStatic) implements SsaInstruction {
        @Override public List<SsaVariable> operands() { return arguments; }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record FieldLoad(SsaVariable result, SsaVariable object, String owner,
                     String name, String descriptor, boolean isStatic) implements SsaInstruction {
        @Override
        public List<SsaVariable> operands() {
            return isStatic ? List.of() : List.of(object);
        }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record FieldStore(SsaVariable object, String owner, String name,
                      String descriptor, SsaVariable value, boolean isStatic) implements SsaInstruction {
        @Override public SsaVariable result() { return null; }
        @Override
        public List<SsaVariable> operands() {
            return isStatic ? List.of(value) : List.of(object, value);
        }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record ArrayLoad(SsaVariable result, SsaVariable array, SsaVariable index) implements SsaInstruction {
        @Override public List<SsaVariable> operands() { return List.of(array, index); }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record ArrayStore(SsaVariable array, SsaVariable index, SsaVariable value) implements SsaInstruction {
        @Override public SsaVariable result() { return null; }
        @Override public List<SsaVariable> operands() { return List.of(array, index, value); }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record Return(SsaVariable value) implements SsaInstruction {
        @Override public SsaVariable result() { return null; }
        @Override
        public List<SsaVariable> operands() {
            return value != null ? List.of(value) : List.of();
        }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record Throw(SsaVariable exception) implements SsaInstruction {
        @Override public SsaVariable result() { return null; }
        @Override public List<SsaVariable> operands() { return List.of(exception); }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record Cast(SsaVariable result, SsaVariable value, String targetType) implements SsaInstruction {
        @Override public List<SsaVariable> operands() { return List.of(value); }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record InstanceOf(SsaVariable result, SsaVariable value, String targetType) implements SsaInstruction {
        @Override public List<SsaVariable> operands() { return List.of(value); }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record New(SsaVariable result, String type) implements SsaInstruction {
        @Override public List<SsaVariable> operands() { return List.of(); }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }

    record NewArray(SsaVariable result, String elementType, SsaVariable size) implements SsaInstruction {
        @Override public List<SsaVariable> operands() { return List.of(size); }
        @Override public void replaceOperand(SsaVariable old, SsaVariable newVar) {}
    }
}
