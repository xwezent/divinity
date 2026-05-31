package com.divinity.ssa;

import com.divinity.cfg.BasicBlock;
import java.util.*;

public final class SsaForm {

    private final List<SsaBasicBlock> blocks;
    private final SsaBasicBlock entryBlock;
    private final Map<String, List<SsaVariable>> variableVersions;
    private final Map<SsaVariable, SsaInstruction> definitions;
    private final Map<SsaVariable, Set<SsaInstruction>> uses;

    public SsaForm(SsaBasicBlock entryBlock, List<SsaBasicBlock> blocks) {
        this.entryBlock = entryBlock;
        this.blocks = blocks;
        this.variableVersions = new LinkedHashMap<>();
        this.definitions = new LinkedHashMap<>();
        this.uses = new LinkedHashMap<>();
        buildDefUseChains();
    }

    private void buildDefUseChains() {
        for (SsaBasicBlock block : blocks) {
            for (SsaInstruction inst : block.instructions()) {
                if (inst.result() != null) {
                    definitions.put(inst.result(), inst);
                    inst.result().setDefinition(inst);
                }
                for (SsaVariable operand : inst.operands()) {
                    uses.computeIfAbsent(operand, k -> new LinkedHashSet<>()).add(inst);
                    operand.addUse(inst);
                }
            }
        }
    }

    public List<SsaBasicBlock> blocks() { return blocks; }
    public SsaBasicBlock entryBlock() { return entryBlock; }
    public Map<String, List<SsaVariable>> variableVersions() { return variableVersions; }
    public Map<SsaVariable, SsaInstruction> definitions() { return definitions; }
    public Map<SsaVariable, Set<SsaInstruction>> uses() { return uses; }

    public void addVariableVersion(String baseName, SsaVariable var) {
        variableVersions.computeIfAbsent(baseName, k -> new ArrayList<>()).add(var);
    }

    public SsaInstruction getDefinition(SsaVariable var) {
        return definitions.get(var);
    }

    public Set<SsaInstruction> getUses(SsaVariable var) {
        return uses.getOrDefault(var, Set.of());
    }

    public void replaceVariable(SsaVariable old, SsaVariable newVar) {
        Set<SsaInstruction> oldUses = uses.get(old);
        if (oldUses != null) {
            for (SsaInstruction use : new ArrayList<>(oldUses)) {
                use.replaceOperand(old, newVar);
                oldUses.remove(use);
                uses.computeIfAbsent(newVar, k -> new LinkedHashSet<>()).add(use);
                old.removeUse(use);
                newVar.addUse(use);
            }
        }
    }

    public void removeInstruction(SsaInstruction inst, SsaBasicBlock block) {
        block.instructions().remove(inst);
        if (inst.result() != null) {
            definitions.remove(inst.result());
            block.definitions().remove(inst.result());
        }
        for (SsaVariable operand : inst.operands()) {
            Set<SsaInstruction> operandUses = uses.get(operand);
            if (operandUses != null) {
                operandUses.remove(inst);
                operand.removeUse(inst);
            }
        }
    }

    public List<SsaBasicBlock> getBlocksInReversePostOrder() {
        List<SsaBasicBlock> result = new ArrayList<>();
        Set<SsaBasicBlock> visited = new HashSet<>();
        postOrderDfs(entryBlock, visited, result);
        Collections.reverse(result);
        return result;
    }

    private void postOrderDfs(SsaBasicBlock block, Set<SsaBasicBlock> visited, List<SsaBasicBlock> result) {
        if (!visited.add(block)) return;
        for (SsaBasicBlock succ : block.successors()) {
            postOrderDfs(succ, visited, result);
        }
        result.add(block);
    }

    public void computeLiveness() {
        List<SsaBasicBlock> rpo = getBlocksInReversePostOrder();
        boolean changed = true;

        while (changed) {
            changed = false;
            for (int i = rpo.size() - 1; i >= 0; i--) {
                SsaBasicBlock block = rpo.get(i);

                Set<SsaVariable> newLiveOut = new LinkedHashSet<>();
                for (SsaBasicBlock succ : block.successors()) {
                    newLiveOut.addAll(succ.liveIn());
                }

                Set<SsaVariable> newLiveIn = new LinkedHashSet<>(newLiveOut);
                newLiveIn.removeAll(block.definitions());
                newLiveIn.addAll(block.uses());

                if (!newLiveIn.equals(block.liveIn()) || !newLiveOut.equals(block.liveOut())) {
                    block.liveIn().clear();
                    block.liveIn().addAll(newLiveIn);
                    block.liveOut().clear();
                    block.liveOut().addAll(newLiveOut);
                    changed = true;
                }
            }
        }
    }
}
