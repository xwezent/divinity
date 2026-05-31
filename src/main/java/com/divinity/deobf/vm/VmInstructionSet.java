package com.divinity.deobf.vm;

import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import java.util.*;
import java.util.function.Function;

public final class VmInstructionSet {

    private final Map<Integer, VmInstruction> instructions;

    public VmInstructionSet() {
        this.instructions = new LinkedHashMap<>();
    }

    public void addInstruction(int opcode, String name, int operandCount,
                               Function<int[], Instruction> translator) {
        instructions.put(opcode, new VmInstruction(opcode, name, operandCount, translator));
    }

    public VmInstruction getInstruction(int opcode) {
        return instructions.get(opcode);
    }

    public static VmInstructionSet zelixInstructionSet() {
        VmInstructionSet set = new VmInstructionSet();

        set.addInstruction(0x00, "NOP", 0,
            ops -> new Instruction(Opcode.NOP, -1));
        set.addInstruction(0x01, "ICONST_0", 0,
            ops -> new Instruction(Opcode.ICONST_0, -1));
        set.addInstruction(0x02, "ICONST_1", 0,
            ops -> new Instruction(Opcode.ICONST_1, -1));
        set.addInstruction(0x03, "ICONST_2", 0,
            ops -> new Instruction(Opcode.ICONST_2, -1));
        set.addInstruction(0x10, "BIPUSH", 1,
            ops -> new Instruction(Opcode.BIPUSH, -1, ops[0]));
        set.addInstruction(0x11, "SIPUSH", 2,
            ops -> new Instruction(Opcode.SIPUSH, -1, ops[0], ops[1]));
        set.addInstruction(0x15, "ILOAD", 1,
            ops -> new Instruction(Opcode.ILOAD, -1, ops[0]));
        set.addInstruction(0x19, "ALOAD", 1,
            ops -> new Instruction(Opcode.ALOAD, -1, ops[0]));
        set.addInstruction(0x36, "ISTORE", 1,
            ops -> new Instruction(Opcode.ISTORE, -1, ops[0]));
        set.addInstruction(0x3A, "ASTORE", 1,
            ops -> new Instruction(Opcode.ASTORE, -1, ops[0]));
        set.addInstruction(0x60, "IADD", 0,
            ops -> new Instruction(Opcode.IADD, -1));
        set.addInstruction(0x64, "ISUB", 0,
            ops -> new Instruction(Opcode.ISUB, -1));
        set.addInstruction(0x68, "IMUL", 0,
            ops -> new Instruction(Opcode.IMUL, -1));
        set.addInstruction(0x6C, "IDIV", 0,
            ops -> new Instruction(Opcode.IDIV, -1));
        set.addInstruction(0x7E, "IAND", 0,
            ops -> new Instruction(Opcode.IAND, -1));
        set.addInstruction(0x80, "IOR", 0,
            ops -> new Instruction(Opcode.IOR, -1));
        set.addInstruction(0x82, "IXOR", 0,
            ops -> new Instruction(Opcode.IXOR, -1));
        set.addInstruction(0xAC, "IRETURN", 0,
            ops -> new Instruction(Opcode.IRETURN, -1));
        set.addInstruction(0xB0, "ARETURN", 0,
            ops -> new Instruction(Opcode.ARETURN, -1));
        set.addInstruction(0xB1, "RETURN", 0,
            ops -> new Instruction(Opcode.RETURN, -1));
        set.addInstruction(0xB6, "INVOKEVIRTUAL", 2,
            ops -> new Instruction(Opcode.INVOKEVIRTUAL, -1, ops[0], ops[1]));
        set.addInstruction(0xB7, "INVOKESPECIAL", 2,
            ops -> new Instruction(Opcode.INVOKESPECIAL, -1, ops[0], ops[1]));
        set.addInstruction(0xB8, "INVOKESTATIC", 2,
            ops -> new Instruction(Opcode.INVOKESTATIC, -1, ops[0], ops[1]));

        return set;
    }

    public static VmInstructionSet dashOInstructionSet() {
        VmInstructionSet set = new VmInstructionSet();

        set.addInstruction(0x00, "PUSH_0", 0,
            ops -> new Instruction(Opcode.ICONST_0, -1));
        set.addInstruction(0x01, "PUSH_1", 0,
            ops -> new Instruction(Opcode.ICONST_1, -1));
        set.addInstruction(0x02, "PUSH_BYTE", 1,
            ops -> new Instruction(Opcode.BIPUSH, -1, ops[0]));
        set.addInstruction(0x03, "PUSH_SHORT", 2,
            ops -> new Instruction(Opcode.SIPUSH, -1, ops[0], ops[1]));
        set.addInstruction(0x10, "LOAD_VAR", 1,
            ops -> new Instruction(Opcode.ILOAD, -1, ops[0]));
        set.addInstruction(0x11, "STORE_VAR", 1,
            ops -> new Instruction(Opcode.ISTORE, -1, ops[0]));
        set.addInstruction(0x20, "ADD", 0,
            ops -> new Instruction(Opcode.IADD, -1));
        set.addInstruction(0x21, "SUB", 0,
            ops -> new Instruction(Opcode.ISUB, -1));
        set.addInstruction(0x22, "MUL", 0,
            ops -> new Instruction(Opcode.IMUL, -1));
        set.addInstruction(0x23, "DIV", 0,
            ops -> new Instruction(Opcode.IDIV, -1));
        set.addInstruction(0x30, "AND", 0,
            ops -> new Instruction(Opcode.IAND, -1));
        set.addInstruction(0x31, "OR", 0,
            ops -> new Instruction(Opcode.IOR, -1));
        set.addInstruction(0x32, "XOR", 0,
            ops -> new Instruction(Opcode.IXOR, -1));
        set.addInstruction(0x40, "CALL", 2,
            ops -> new Instruction(Opcode.INVOKEVIRTUAL, -1, ops[0], ops[1]));
        set.addInstruction(0x50, "RETURN_INT", 0,
            ops -> new Instruction(Opcode.IRETURN, -1));
        set.addInstruction(0x51, "RETURN_VOID", 0,
            ops -> new Instruction(Opcode.RETURN, -1));

        return set;
    }

    public record VmInstruction(int opcode, String name, int operandCount,
                                Function<int[], Instruction> translator) {

        public Instruction toJavaInstruction(int[] operands) {
            if (operands.length < operandCount) {
                return null;
            }
            try {
                return translator.apply(operands);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public String toString() {
            return String.format("VM[0x%02X] %s (operands: %d)", opcode, name, operandCount);
        }
    }
}
