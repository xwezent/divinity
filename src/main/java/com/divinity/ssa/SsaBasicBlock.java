package com.divinity.ssa;

import com.divinity.cfg.BasicBlock;
import java.util.*;

public final class SsaBasicBlock {

    private final int id;
    private final BasicBlock originalBlock;
    private final List<SsaInstruction> instructions;
    private final List<SsaBasicBlock> successors;
    private final List<SsaBasicBlock> predecessors;
    private final Set<SsaVariable> liveIn;
    private final Set<SsaVariable> liveOut;
    private final Set<SsaVariable> definitions;
    private final Set<SsaVariable> uses;
    private SsaBasicBlock immediateDominator;
    private final Set<SsaBasicBlock> dominanceFrontier;

    public SsaBasicBlock(int id, BasicBlock originalBlock) {
        this.id = id;
        this.originalBlock = originalBlock;
        this.instructions = new ArrayList<>();
        this.successors = new ArrayList<>();
        this.predecessors = new ArrayList<>();
        this.liveIn = new LinkedHashSet<>();
        this.liveOut = new LinkedHashSet<>();
        this.definitions = new LinkedHashSet<>();
        this.uses = new LinkedHashSet<>();
        this.dominanceFrontier = new LinkedHashSet<>();
    }

    public int id() { return id; }
    public BasicBlock originalBlock() { return originalBlock; }
    public List<SsaInstruction> instructions() { return instructions; }
    public List<SsaBasicBlock> successors() { return successors; }
    public List<SsaBasicBlock> predecessors() { return predecessors; }
    public Set<SsaVariable> liveIn() { return liveIn; }
    public Set<SsaVariable> liveOut() { return liveOut; }
    public Set<SsaVariable> definitions() { return definitions; }
    public Set<SsaVariable> uses() { return uses; }
    public SsaBasicBlock immediateDominator() { return immediateDominator; }
    public Set<SsaBasicBlock> dominanceFrontier() { return dominanceFrontier; }

    public void setImmediateDominator(SsaBasicBlock dom) {
        this.immediateDominator = dom;
    }

    public void addInstruction(SsaInstruction inst) {
        instructions.add(inst);
        if (inst.result() != null) {
            definitions.add(inst.result());
        }
        for (SsaVariable operand : inst.operands()) {
            uses.add(operand);
        }
    }

    public void addSuccessor(SsaBasicBlock block) {
        if (!successors.contains(block)) {
            successors.add(block);
        }
    }

    public void addPredecessor(SsaBasicBlock block) {
        if (!predecessors.contains(block)) {
            predecessors.add(block);
        }
    }

    public boolean dominates(SsaBasicBlock other) {
        if (this == other) return true;
        SsaBasicBlock current = other.immediateDominator;
        Set<SsaBasicBlock> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (current == this) return true;
            current = current.immediateDominator;
        }
        return false;
    }

    public boolean strictlyDominates(SsaBasicBlock other) {
        return this != other && dominates(other);
    }

    @Override
    public String toString() {
        return "BB" + id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SsaBasicBlock b)) return false;
        return id == b.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
