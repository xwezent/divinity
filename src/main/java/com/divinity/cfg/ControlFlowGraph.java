package com.divinity.cfg;

import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import java.util.*;

public final class ControlFlowGraph {

    private final List<Instruction> instructions;
    private final int[][] exceptionTable;
    private final String[] exceptionTypes;
    private final List<BasicBlock> blocks;
    private BasicBlock entryBlock;
    private final Map<Integer, BasicBlock> leaderToBlock;

    public ControlFlowGraph(List<Instruction> instructions,
                            int[][] exceptionTable,
                            String[] exceptionTypes) {
        this.instructions = instructions;
        this.exceptionTable = exceptionTable != null ? exceptionTable : new int[0][0];
        this.exceptionTypes = exceptionTypes != null ? exceptionTypes : new String[0];
        this.blocks = new ArrayList<>();
        this.leaderToBlock = new LinkedHashMap<>();
        build();
    }

    private void build() {
        if (instructions.isEmpty()) {
            entryBlock = new BasicBlock(0, 0);
            blocks.add(entryBlock);
            return;
        }

        Set<Integer> leaders = findLeaders();
        List<Integer> sortedLeaders = new ArrayList<>(leaders);
        sortedLeaders.sort(Integer::compareTo);

        Map<Integer, Integer> offsetToBlockIdx = new HashMap<>();

        for (int i = 0; i < sortedLeaders.size(); i++) {
            int start = sortedLeaders.get(i);
            int end = instructions.get(instructions.size() - 1).offset;
            BasicBlock block = new BasicBlock(i, start);
            blocks.add(block);
            leaderToBlock.put(start, block);
            offsetToBlockIdx.put(start, i);
        }

        for (int i = 0; i < blocks.size(); i++) {
            BasicBlock block = blocks.get(i);
            int start = block.startOffset;
            int endIdx = i + 1 < blocks.size()
                    ? findInstructionIndex(blocks.get(i + 1).startOffset)
                    : instructions.size();

            for (int j = findInstructionIndex(start); j < endIdx; j++) {
                block.instructions.add(instructions.get(j));
            }

            if (!block.instructions.isEmpty()) {
                block.endOffset = block.instructions.get(block.instructions.size() - 1).offset;
            } else {
                block.endOffset = start;
            }
        }

        buildSuccessors();
        buildExceptionEdges(offsetToBlockIdx);
        buildPredecessors();

        entryBlock = blocks.isEmpty() ? null : blocks.get(0);
    }

    private Set<Integer> findLeaders() {
        Set<Integer> leaders = new LinkedHashSet<>();
        leaders.add(instructions.get(0).offset);

        Map<Integer, Instruction> byOffset = new LinkedHashMap<>();
        for (Instruction inst : instructions) {
            byOffset.put(inst.offset, inst);
        }

        for (Instruction inst : instructions) {
            if (inst.opcode.isBranch()) {
                int[] targets = getBranchTargets(inst, byOffset);
                for (int t : targets) {
                    leaders.add(t);
                }
            }

            if (inst.opcode.isReturn() || inst.opcode == Opcode.ATHROW
                    || inst.opcode == Opcode.GOTO || inst.opcode == Opcode.GOTO_W) {
                if (instructions.indexOf(inst) + 1 < instructions.size()) {
                    leaders.add(instructions.get(instructions.indexOf(inst) + 1).offset);
                }
            }
        }

        for (int[] handler : exceptionTable) {
            leaders.add(handler[2]);
        }

        return leaders;
    }

    private int[] getBranchTargets(Instruction inst, Map<Integer, Instruction> byOffset) {
        int offset = inst.offset;
        return switch (inst.opcode) {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                 IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL -> {
                int branch = offset + (short) ((inst.operands[0] << 8) | inst.operands[1]);
                yield new int[]{branch};
            }
            case GOTO -> {
                int branch = offset + (short) ((inst.operands[0] << 8) | inst.operands[1]);
                yield new int[]{branch};
            }
            case GOTO_W -> {
                int branch = offset + ((inst.operands[0] << 24) | (inst.operands[1] << 16)
                        | (inst.operands[2] << 8) | inst.operands[3]);
                yield new int[]{branch};
            }
            case TABLESWITCH -> {
                int[] targets = new int[inst.operands.length - 2];
                targets[0] = inst.operands[0];
                for (int i = 0; i < inst.operands.length - 3; i++) {
                    targets[i + 1] = inst.operands[3 + i];
                }
                yield targets;
            }
            case LOOKUPSWITCH -> {
                if (inst.operands.length < 2) yield new int[0];
                int nPairs = inst.operands[1];
                if (nPairs < 0 || nPairs > 10000) yield new int[]{inst.operands[0]};
                int[] targets = new int[nPairs + 1];
                targets[0] = inst.operands[0];
                for (int i = 0; i < nPairs; i++) {
                    targets[i + 1] = inst.operands[2 + i * 2 + 1];
                }
                yield targets;
            }
            default -> new int[0];
        };
    }

    private int findInstructionIndex(int offset) {
        for (int i = 0; i < instructions.size(); i++) {
            if (instructions.get(i).offset >= offset) return i;
        }
        return instructions.size();
    }

    private void buildSuccessors() {
        Map<Integer, BasicBlock> leaderMap = new HashMap<>();
        for (BasicBlock b : blocks) {
            leaderMap.put(b.startOffset, b);
        }

        for (int i = 0; i < blocks.size(); i++) {
            BasicBlock block = blocks.get(i);
            Instruction last = block.lastInstruction();
            if (last == null) continue;

            if (last.opcode.isBranch()) {
                int[] targets = getBranchTargets(last,
                        instructions.stream()
                                .collect(LinkedHashMap::new, (m, ins) -> m.put(ins.offset, ins), Map::putAll));
                for (int target : targets) {
                    BasicBlock succ = leaderMap.get(target);
                    if (succ != null && succ != block) {
                        block.successors.add(succ);
                    }
                }
            }

            if (last.fallsThrough() && i + 1 < blocks.size()) {
                BasicBlock next = blocks.get(i + 1);
                if (!block.successors.contains(next)) {
                    block.successors.add(next);
                }
            }
        }
    }

    private void buildExceptionEdges(Map<Integer, Integer> offsetToBlockIdx) {
        for (int[] handler : exceptionTable) {
            int handlerPc = handler[2];

            Integer handlerBlockIdx = null;
            for (BasicBlock bb : blocks) {
                if (bb.startOffset == handlerPc || bb.startOffset <= handlerPc && handlerPc < bb.endOffset + 10) {
                    handlerBlockIdx = bb.id;
                    break;
                }
            }
            if (handlerBlockIdx == null) continue;

            BasicBlock handlerBlock = blocks.get(handlerBlockIdx);
            handlerBlock.isExceptionHandler = true;
            handlerBlock.handlerType = handler.length > 3 ? exceptionTypes[handler[3]] : null;

            for (BasicBlock bb : blocks) {
                if (bb.startOffset >= handler[0] && bb.endOffset < handler[1]) {
                    if (!bb.successors.contains(handlerBlock)) {
                        bb.successors.add(handlerBlock);
                        bb.exceptionHandlers.add(handlerBlock);
                    }
                }
            }
        }
    }

    private void buildPredecessors() {
        for (BasicBlock block : blocks) {
            for (BasicBlock succ : block.successors) {
                if (!succ.predecessors.contains(block)) {
                    succ.predecessors.add(block);
                }
            }
        }
    }

    public void computeDominators() {
        if (blocks.isEmpty() || entryBlock == null) return;

        int n = blocks.size();
        BitSet[] dom = new BitSet[n];
        for (int i = 0; i < n; i++) {
            dom[i] = new BitSet(n);
            dom[i].set(0, n);
        }

        dom[entryBlock.id].clear();
        dom[entryBlock.id].set(entryBlock.id);

        boolean changed = true;
        while (changed) {
            changed = false;
            for (BasicBlock block : blocks) {
                if (block == entryBlock) continue;

                BitSet newDom = null;
                for (BasicBlock pred : block.predecessors) {
                    if (newDom == null) {
                        newDom = (BitSet) dom[pred.id].clone();
                    } else {
                        newDom.and(dom[pred.id]);
                    }
                }

                if (newDom == null) newDom = new BitSet(n);
                newDom.set(block.id);

                if (!newDom.equals(dom[block.id])) {
                    dom[block.id] = newDom;
                    changed = true;
                }
            }
        }

        for (BasicBlock block : blocks) {
            BitSet blockDom = dom[block.id];
            blockDominatorSearch:
            for (int i = blockDom.nextSetBit(0); i >= 0; i = blockDom.nextSetBit(i + 1)) {
                if (i == block.id) continue;
                BasicBlock candidate = blocks.get(i);

                for (int j = blockDom.nextSetBit(0); j >= 0; j = blockDom.nextSetBit(j + 1)) {
                    if (j == block.id || j == i) continue;
                    if (dom[j].get(i)) {
                        continue blockDominatorSearch;
                    }
                }

                block.immediateDominator = candidate;
                break;
            }
        }

        for (BasicBlock block : blocks) {
            if (block.immediateDominator == null) continue;
            for (BasicBlock other : blocks) {
                if (other == block) continue;
                if (dom[other.id].get(block.id) && other.immediateDominator != block
                        && dom[block.id].get(other.immediateDominator != null ? other.immediateDominator.id : -1)) {
                    block.dominanceFrontier.add(other);
                }
            }
            for (BasicBlock succ : block.successors) {
                if (succ.immediateDominator != block) {
                    block.dominanceFrontier.add(succ);
                }
            }
        }
    }

    public List<List<BasicBlock>> findLoops() {
        List<List<BasicBlock>> loops = new ArrayList<>();
        Set<BasicBlock> visited = new LinkedHashSet<>();

        for (BasicBlock block : blocks) {
            for (BasicBlock succ : block.successors) {
                if (isBackEdge(block, succ)) {
                    List<BasicBlock> loop = new ArrayList<>();
                    collectLoopBody(succ, block, loop, visited);
                    if (!loop.isEmpty()) {
                        loops.add(loop);
                    }
                }
            }
        }

        return loops;
    }

    private boolean isBackEdge(BasicBlock from, BasicBlock to) {
        if (from.immediateDominator == null) return false;
        return to == from || dominates(to, from);
    }

    private boolean dominates(BasicBlock a, BasicBlock b) {
        if (a == b) return true;
        BasicBlock current = b;
        Set<BasicBlock> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            if (current == a) return true;
            current = current.immediateDominator;
        }
        return false;
    }

    private void collectLoopBody(BasicBlock header, BasicBlock tail,
                                  List<BasicBlock> loop, Set<BasicBlock> visited) {
        if (!visited.add(header)) return;
        loop.add(header);
        if (header != tail) {
            for (BasicBlock pred : header.predecessors) {
                collectLoopBody(pred, tail, loop, visited);
            }
        }
    }

    public void removeNopInstructions() {
        for (BasicBlock block : blocks) {
            block.instructions.removeIf(i -> i.opcode == Opcode.NOP);
        }
    }

    public void collapseGotoChains() {
        Map<BasicBlock, BasicBlock> chain = new HashMap<>();
        for (BasicBlock block : blocks) {
            if (block.instructions.size() == 1 && block.endsWithGoto()
                    && block.successors.size() == 1) {
                chain.put(block, block.successors.get(0));
            }
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (var entry : new HashMap<>(chain).entrySet()) {
                BasicBlock target = chain.get(entry.getValue());
                if (target != null && target != entry.getKey()) {
                    chain.put(entry.getKey(), target);
                    changed = true;
                }
            }
        }

        for (var entry : chain.entrySet()) {
            BasicBlock from = entry.getKey();
            BasicBlock to = entry.getValue();
            for (BasicBlock pred : from.predecessors) {
                pred.successors.remove(from);
                if (!pred.successors.contains(to)) {
                    pred.successors.add(to);
                }
                to.predecessors.remove(from);
                if (!to.predecessors.contains(pred)) {
                    to.predecessors.add(pred);
                }
            }
        }
    }

    public List<BasicBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public BasicBlock getEntryBlock() {
        return entryBlock;
    }

    public int size() {
        return blocks.size();
    }

    public List<Instruction> getInstructions() {
        return Collections.unmodifiableList(instructions);
    }
}
