package com.divinity.ssa;

import com.divinity.cfg.BasicBlock;
import com.divinity.cfg.ControlFlowGraph;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import java.util.*;

public final class SsaBuilder {

    private final ControlFlowGraph cfg;
    private final Map<BasicBlock, SsaBasicBlock> blockMap;
    private final Map<String, Integer> variableCounters;
    private final Map<String, Deque<SsaVariable>> variableStacks;
    private int blockIdCounter = 0;

    public SsaBuilder(ControlFlowGraph cfg) {
        this.cfg = cfg;
        this.blockMap = new LinkedHashMap<>();
        this.variableCounters = new LinkedHashMap<>();
        this.variableStacks = new LinkedHashMap<>();
    }

    public SsaForm build() {
        List<SsaBasicBlock> ssaBlocks = createSsaBlocks();
        SsaBasicBlock entryBlock = blockMap.get(cfg.getEntryBlock());

        computeDominators(ssaBlocks, entryBlock);
        computeDominanceFrontiers(ssaBlocks);
        insertPhiFunctions(ssaBlocks);
        renameVariables(entryBlock, new HashSet<>());

        return new SsaForm(entryBlock, ssaBlocks);
    }

    private List<SsaBasicBlock> createSsaBlocks() {
        List<SsaBasicBlock> ssaBlocks = new ArrayList<>();

        for (BasicBlock block : cfg.getBlocks()) {
            SsaBasicBlock ssaBlock = new SsaBasicBlock(blockIdCounter++, block);
            blockMap.put(block, ssaBlock);
            ssaBlocks.add(ssaBlock);
        }

        for (BasicBlock block : cfg.getBlocks()) {
            SsaBasicBlock ssaBlock = blockMap.get(block);
            for (BasicBlock succ : block.successors) {
                SsaBasicBlock ssaSucc = blockMap.get(succ);
                if (ssaSucc != null) {
                    ssaBlock.addSuccessor(ssaSucc);
                    ssaSucc.addPredecessor(ssaBlock);
                }
            }
        }

        return ssaBlocks;
    }

    private void computeDominators(List<SsaBasicBlock> blocks, SsaBasicBlock entry) {
        Map<SsaBasicBlock, Set<SsaBasicBlock>> dominators = new LinkedHashMap<>();

        for (SsaBasicBlock block : blocks) {
            dominators.put(block, new LinkedHashSet<>(blocks));
        }
        dominators.put(entry, Set.of(entry));

        boolean changed = true;
        while (changed) {
            changed = false;
            for (SsaBasicBlock block : blocks) {
                if (block == entry) continue;

                Set<SsaBasicBlock> newDom = null;
                for (SsaBasicBlock pred : block.predecessors()) {
                    Set<SsaBasicBlock> predDom = dominators.get(pred);
                    if (newDom == null) {
                        newDom = new LinkedHashSet<>(predDom);
                    } else {
                        newDom.retainAll(predDom);
                    }
                }

                if (newDom == null) newDom = new LinkedHashSet<>();
                newDom.add(block);

                if (!newDom.equals(dominators.get(block))) {
                    dominators.put(block, newDom);
                    changed = true;
                }
            }
        }

        for (SsaBasicBlock block : blocks) {
            Set<SsaBasicBlock> blockDom = new LinkedHashSet<>(dominators.get(block));
            blockDom.remove(block);

            Set<SsaBasicBlock> candidates = new LinkedHashSet<>(blockDom);
            for (SsaBasicBlock d1 : blockDom) {
                for (SsaBasicBlock d2 : blockDom) {
                    if (d1 != d2 && dominators.get(d2).contains(d1)) {
                        candidates.remove(d2);
                    }
                }
            }

            if (candidates.size() == 1) {
                block.setImmediateDominator(candidates.iterator().next());
            }
        }
    }

    private void computeDominanceFrontiers(List<SsaBasicBlock> blocks) {
        for (SsaBasicBlock block : blocks) {
            if (block.predecessors().size() >= 2) {
                for (SsaBasicBlock pred : block.predecessors()) {
                    SsaBasicBlock runner = pred;
                    while (runner != null && !runner.strictlyDominates(block)) {
                        runner.dominanceFrontier().add(block);
                        runner = runner.immediateDominator();
                    }
                }
            }
        }
    }

    private void insertPhiFunctions(List<SsaBasicBlock> blocks) {
        Set<String> allVariables = collectAllVariables();

        for (String varName : allVariables) {
            Set<SsaBasicBlock> defBlocks = new LinkedHashSet<>();
            for (SsaBasicBlock block : blocks) {
                if (blockDefinesVariable(block.originalBlock(), varName)) {
                    defBlocks.add(block);
                }
            }

            Set<SsaBasicBlock> phiBlocks = new LinkedHashSet<>();
            Deque<SsaBasicBlock> worklist = new ArrayDeque<>(defBlocks);

            while (!worklist.isEmpty()) {
                SsaBasicBlock block = worklist.poll();
                for (SsaBasicBlock df : block.dominanceFrontier()) {
                    if (phiBlocks.add(df)) {
                        SsaVariable phiResult = newVariable(varName, "Ljava/lang/Object;");
                        Map<Integer, SsaVariable> incoming = new LinkedHashMap<>();
                        SsaInstruction.Phi phi = new SsaInstruction.Phi(phiResult, incoming);
                        df.instructions().add(0, phi);
                        df.definitions().add(phiResult);

                        if (!defBlocks.contains(df)) {
                            worklist.add(df);
                        }
                    }
                }
            }
        }
    }

    private Set<String> collectAllVariables() {
        Set<String> variables = new LinkedHashSet<>();
        for (BasicBlock block : cfg.getBlocks()) {
            for (Instruction inst : block.instructions) {
                String varName = getVariableFromInstruction(inst);
                if (varName != null) {
                    variables.add(varName);
                }
            }
        }
        return variables;
    }

    private boolean blockDefinesVariable(BasicBlock block, String varName) {
        for (Instruction inst : block.instructions) {
            if (isStoreInstruction(inst.opcode)) {
                String defVar = getVariableFromInstruction(inst);
                if (varName.equals(defVar)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getVariableFromInstruction(Instruction inst) {
        if (isLoadInstruction(inst.opcode) || isStoreInstruction(inst.opcode)) {
            int index = getVarIndex(inst);
            return "var" + index;
        }
        return null;
    }

    private boolean isLoadInstruction(Opcode op) {
        return op == Opcode.ILOAD || op == Opcode.LLOAD || op == Opcode.FLOAD ||
               op == Opcode.DLOAD || op == Opcode.ALOAD ||
               op == Opcode.ILOAD_0 || op == Opcode.ILOAD_1 || op == Opcode.ILOAD_2 || op == Opcode.ILOAD_3 ||
               op == Opcode.LLOAD_0 || op == Opcode.LLOAD_1 || op == Opcode.LLOAD_2 || op == Opcode.LLOAD_3 ||
               op == Opcode.FLOAD_0 || op == Opcode.FLOAD_1 || op == Opcode.FLOAD_2 || op == Opcode.FLOAD_3 ||
               op == Opcode.DLOAD_0 || op == Opcode.DLOAD_1 || op == Opcode.DLOAD_2 || op == Opcode.DLOAD_3 ||
               op == Opcode.ALOAD_0 || op == Opcode.ALOAD_1 || op == Opcode.ALOAD_2 || op == Opcode.ALOAD_3;
    }

    private boolean isStoreInstruction(Opcode op) {
        return op == Opcode.ISTORE || op == Opcode.LSTORE || op == Opcode.FSTORE ||
               op == Opcode.DSTORE || op == Opcode.ASTORE ||
               op == Opcode.ISTORE_0 || op == Opcode.ISTORE_1 || op == Opcode.ISTORE_2 || op == Opcode.ISTORE_3 ||
               op == Opcode.LSTORE_0 || op == Opcode.LSTORE_1 || op == Opcode.LSTORE_2 || op == Opcode.LSTORE_3 ||
               op == Opcode.FSTORE_0 || op == Opcode.FSTORE_1 || op == Opcode.FSTORE_2 || op == Opcode.FSTORE_3 ||
               op == Opcode.DSTORE_0 || op == Opcode.DSTORE_1 || op == Opcode.DSTORE_2 || op == Opcode.DSTORE_3 ||
               op == Opcode.ASTORE_0 || op == Opcode.ASTORE_1 || op == Opcode.ASTORE_2 || op == Opcode.ASTORE_3;
    }

    private int getVarIndex(Instruction inst) {
        return switch (inst.opcode) {
            case ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
                 ISTORE, LSTORE, FSTORE, DSTORE, ASTORE ->
                inst.operands.length > 0 ? inst.operands[0] & 0xFF : 0;
            case ILOAD_0, LLOAD_0, FLOAD_0, DLOAD_0, ALOAD_0,
                 ISTORE_0, LSTORE_0, FSTORE_0, DSTORE_0, ASTORE_0 -> 0;
            case ILOAD_1, LLOAD_1, FLOAD_1, DLOAD_1, ALOAD_1,
                 ISTORE_1, LSTORE_1, FSTORE_1, DSTORE_1, ASTORE_1 -> 1;
            case ILOAD_2, LLOAD_2, FLOAD_2, DLOAD_2, ALOAD_2,
                 ISTORE_2, LSTORE_2, FSTORE_2, DSTORE_2, ASTORE_2 -> 2;
            case ILOAD_3, LLOAD_3, FLOAD_3, DLOAD_3, ALOAD_3,
                 ISTORE_3, LSTORE_3, FSTORE_3, DSTORE_3, ASTORE_3 -> 3;
            default -> 0;
        };
    }

    private void renameVariables(SsaBasicBlock block, Set<SsaBasicBlock> visited) {
        if (!visited.add(block)) return;

        Map<String, Integer> savedCounters = new LinkedHashMap<>();
        for (String var : variableStacks.keySet()) {
            savedCounters.put(var, variableStacks.get(var).size());
        }

        for (int i = 0; i < block.instructions().size(); i++) {
            SsaInstruction inst = block.instructions().get(i);

            if (inst instanceof SsaInstruction.Phi phi) {
                pushVariable(phi.result().baseName(), phi.result());
            }
        }

        for (SsaBasicBlock succ : block.successors()) {
            for (SsaInstruction inst : succ.instructions()) {
                if (inst instanceof SsaInstruction.Phi phi) {
                    for (String varName : variableStacks.keySet()) {
                        if (phi.result().baseName().equals(varName)) {
                            SsaVariable current = peekVariable(varName);
                            if (current != null) {
                                phi.addIncoming(block.id(), current);
                            }
                        }
                    }
                }
            }
        }

        for (SsaBasicBlock succ : block.successors()) {
            renameVariables(succ, visited);
        }

        for (String var : savedCounters.keySet()) {
            int savedSize = savedCounters.get(var);
            Deque<SsaVariable> stack = variableStacks.get(var);
            while (stack.size() > savedSize) {
                stack.pop();
            }
        }
    }

    private SsaVariable newVariable(String baseName, String descriptor) {
        int version = variableCounters.getOrDefault(baseName, 0);
        variableCounters.put(baseName, version + 1);
        return new SsaVariable(baseName, version, descriptor);
    }

    private void pushVariable(String baseName, SsaVariable var) {
        variableStacks.computeIfAbsent(baseName, k -> new ArrayDeque<>()).push(var);
    }

    private SsaVariable peekVariable(String baseName) {
        Deque<SsaVariable> stack = variableStacks.get(baseName);
        return stack != null && !stack.isEmpty() ? stack.peek() : null;
    }
}
