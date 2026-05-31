package com.divinity.cfg;

import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import java.util.*;

public final class BasicBlock {
    public final int id;
    public final int startOffset;
    public int endOffset;
    public final List<Instruction> instructions;
    public final List<BasicBlock> predecessors;
    public final List<BasicBlock> successors;
    public final List<BasicBlock> exceptionHandlers;
    public boolean isExceptionHandler;
    public String handlerType;
    public boolean visited;
    public int postOrderNumber;
    public BasicBlock immediateDominator;
    public final Set<BasicBlock> dominanceFrontier;

    public BasicBlock(int id, int startOffset) {
        this.id = id;
        this.startOffset = startOffset;
        this.instructions = new ArrayList<>();
        this.predecessors = new ArrayList<>();
        this.successors = new ArrayList<>();
        this.exceptionHandlers = new ArrayList<>();
        this.dominanceFrontier = new LinkedHashSet<>();
    }

    public Instruction firstInstruction() {
        return instructions.isEmpty() ? null : instructions.get(0);
    }

    public Instruction lastInstruction() {
        return instructions.isEmpty() ? null : instructions.get(instructions.size() - 1);
    }

    public boolean isReturnBlock() {
        Instruction last = lastInstruction();
        return last != null && last.opcode.isReturn();
    }

    public boolean isThrowBlock() {
        Instruction last = lastInstruction();
        return last != null && last.opcode == Opcode.ATHROW;
    }

    public boolean endsWithGoto() {
        Instruction last = lastInstruction();
        return last != null && (last.opcode == Opcode.GOTO || last.opcode == Opcode.GOTO_W);
    }

    public int size() {
        return instructions.size();
    }

    @Override
    public String toString() {
        return String.format("BB#%d [%d-%d] insns:%d preds:%d succs:%d",
                id, startOffset, endOffset, instructions.size(),
                predecessors.size(), successors.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BasicBlock that)) return false;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return id;
    }
}
