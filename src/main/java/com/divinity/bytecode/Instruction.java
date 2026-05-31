package com.divinity.bytecode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Instruction {
    public final Opcode opcode;
    public final int offset;
    public final int[] operands;
    public final List<Instruction> predecessors;
    public final List<Instruction> successors;

    public Instruction(Opcode opcode, int offset, int... operands) {
        this.opcode = Objects.requireNonNull(opcode);
        this.offset = offset;
        this.operands = operands.clone();
        this.predecessors = new ArrayList<>(2);
        this.successors = new ArrayList<>(2);
    }

    public int byteSize() {
        int size = 1;
        if (opcode.operandSize >= 0) {
            size += opcode.operandSize;
        } else if (opcode == Opcode.TABLESWITCH || opcode == Opcode.LOOKUPSWITCH) {
            int padding = (4 - (offset + 1) % 4) % 4;
            size += padding;
            if (opcode == Opcode.TABLESWITCH) {
                size += 12 + operands[2] * 4;
            } else {
                size += 8 + operands[1] * 8;
            }
        } else if (opcode == Opcode.WIDE) {
            size += operands[0] == 0x84 ? 5 : 3;
        }
        return size;
    }

    public boolean isBranch() {
        return opcode.isBranch();
    }

    public boolean fallsThrough() {
        return !opcode.isUnconditionalBranch() && !opcode.isReturn() && opcode != Opcode.ATHROW;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%04d: %s", offset, opcode.name().toLowerCase()));
        for (int op : operands) {
            sb.append(' ').append(op);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Instruction that)) return false;
        return offset == that.offset && opcode == that.opcode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(opcode, offset);
    }
}
