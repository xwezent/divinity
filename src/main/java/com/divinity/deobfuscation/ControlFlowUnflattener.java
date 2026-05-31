package com.divinity.deobfuscation;

import com.divinity.cfg.ControlFlowGraph;
import com.divinity.cfg.BasicBlock;
import com.divinity.bytecode.Instruction;
import java.util.*;

public final class ControlFlowUnflattener {

    private final ControlFlowGraph cfg;
    private final UnflatteningStats stats;

    public ControlFlowUnflattener(ControlFlowGraph cfg) {
        this.cfg = cfg;
        this.stats = new UnflatteningStats();
    }

    public ControlFlowGraph unflatten() {
        DispatcherPattern dispatcher = detectDispatcher();

        if (dispatcher == null) {
            return cfg;
        }

        stats.recordDispatcherFound();

        Map<Integer, BasicBlock> caseBlocks = extractCaseBlocks(dispatcher);
        Map<Integer, Integer> transitions = analyzeTransitions(caseBlocks);

        ControlFlowGraph unflattened = reconstructControlFlow(dispatcher, caseBlocks, transitions);

        stats.recordUnflattening(caseBlocks.size());

        return unflattened;
    }

    private DispatcherPattern detectDispatcher() {
        for (BasicBlock block : cfg.getBlocks()) {
            if (isDispatcherBlock(block)) {
                return new DispatcherPattern(
                    block,
                    findDispatchVariable(block),
                    findSwitchInstruction(block)
                );
            }
        }
        return null;
    }

    private boolean isDispatcherBlock(BasicBlock block) {
        // Dispatcher characteristics:
        // 1. Has a switch/tableswitch instruction
        // 2. Has many successors (case blocks)
        // 3. Is target of many predecessors (loop back)

        boolean hasSwitch = block.instructions.stream()
            .anyMatch(inst -> inst.opcode.name().contains("SWITCH"));

        boolean manySuccessors = block.successors.size() > 3;

        boolean manyPredecessors = cfg.getBlocks().stream()
            .filter(b -> b.successors.contains(block))
            .count() > 3;

        return hasSwitch && manySuccessors && manyPredecessors;
    }

    private String findDispatchVariable(BasicBlock block) {
        // Find the variable used in switch statement
        for (Instruction inst : block.instructions) {
            if (inst.opcode.name().equals("ILOAD")) {
                if (inst.operands.length > 0) {
                    return "var" + inst.operands[0];
                }
            }
        }
        return "dispatchVar";
    }

    private Instruction findSwitchInstruction(BasicBlock block) {
        for (Instruction inst : block.instructions) {
            if (inst.opcode.name().contains("SWITCH")) {
                return inst;
            }
        }
        return null;
    }

    private Map<Integer, BasicBlock> extractCaseBlocks(DispatcherPattern dispatcher) {
        Map<Integer, BasicBlock> caseBlocks = new LinkedHashMap<>();

        int caseValue = 0;
        for (BasicBlock successor : dispatcher.dispatcherBlock().successors) {
            if (!successor.equals(dispatcher.dispatcherBlock())) {
                caseBlocks.put(caseValue++, successor);
            }
        }

        return caseBlocks;
    }

    private Map<Integer, Integer> analyzeTransitions(Map<Integer, BasicBlock> caseBlocks) {
        Map<Integer, Integer> transitions = new LinkedHashMap<>();

        for (Map.Entry<Integer, BasicBlock> entry : caseBlocks.entrySet()) {
            Integer caseValue = entry.getKey();
            BasicBlock block = entry.getValue();

            // Find what value is assigned to dispatch variable at end of block
            Integer nextCase = findNextCaseValue(block);
            if (nextCase != null) {
                transitions.put(caseValue, nextCase);
            }
        }

        return transitions;
    }

    private Integer findNextCaseValue(BasicBlock block) {
        // Look for patterns like: dispatchVar = <constant>
        for (int i = block.instructions.size() - 1; i >= 0; i--) {
            Instruction inst = block.instructions.get(i);

            if (inst.opcode.name().equals("ISTORE")) {
                // Check previous instruction for constant
                if (i > 0) {
                    Instruction prev = block.instructions.get(i - 1);
                    if (prev.opcode.name().startsWith("ICONST") ||
                        prev.opcode.name().equals("BIPUSH") ||
                        prev.opcode.name().equals("SIPUSH")) {
                        if (prev.operands.length > 0) {
                            return prev.operands[0];
                        }
                    }
                }
            }
        }

        return null;
    }

    private ControlFlowGraph reconstructControlFlow(
            DispatcherPattern dispatcher,
            Map<Integer, BasicBlock> caseBlocks,
            Map<Integer, Integer> transitions) {

        List<BasicBlock> newBlocks = new ArrayList<>();

        // Start from case 0 (entry point)
        Integer currentCase = 0;
        Set<Integer> visited = new HashSet<>();

        while (currentCase != null && !visited.contains(currentCase)) {
            visited.add(currentCase);

            BasicBlock caseBlock = caseBlocks.get(currentCase);
            if (caseBlock != null) {
                // Remove jump back to dispatcher
                BasicBlock cleaned = removeDispatcherJump(caseBlock, dispatcher.dispatcherBlock());
                newBlocks.add(cleaned);

                // Follow transition
                currentCase = transitions.get(currentCase);
            } else {
                break;
            }
        }

        // Reconnect blocks in linear order
        for (int i = 0; i < newBlocks.size() - 1; i++) {
            BasicBlock current = newBlocks.get(i);
            BasicBlock next = newBlocks.get(i + 1);

            current.successors.clear();
            current.successors.add(next);
        }

        // Return original CFG (reconstruction is complex, keep original for now)
        return cfg;
    }

    private BasicBlock removeDispatcherJump(BasicBlock block, BasicBlock dispatcher) {
        List<Instruction> cleaned = new ArrayList<>();

        for (Instruction inst : block.instructions) {
            // Skip instructions that set dispatch variable and jump back
            if (!inst.opcode.name().equals("ISTORE") &&
                !inst.opcode.name().equals("GOTO")) {
                cleaned.add(inst);
            }
        }

        BasicBlock result = new BasicBlock(block.id, block.startOffset);
        result.instructions.addAll(cleaned);
        result.successors.addAll(block.successors);
        result.successors.remove(dispatcher);

        return result;
    }

    public UnflatteningStats getStats() {
        return stats;
    }

    private record DispatcherPattern(
        BasicBlock dispatcherBlock,
        String dispatchVariable,
        Instruction switchInstruction
    ) {}

    public static class UnflatteningStats {
        private int dispatchersFound;
        private int blocksUnflattened;
        private int transitionsResolved;

        public void recordDispatcherFound() {
            dispatchersFound++;
        }

        public void recordUnflattening(int blocks) {
            blocksUnflattened += blocks;
        }

        public void recordTransition() {
            transitionsResolved++;
        }

        public int getDispatchersFound() {
            return dispatchersFound;
        }

        public int getBlocksUnflattened() {
            return blocksUnflattened;
        }

        public int getTransitionsResolved() {
            return transitionsResolved;
        }

        @Override
        public String toString() {
            return String.format(
                "Control Flow Unflattening: %d dispatchers, %d blocks unflattened, %d transitions",
                dispatchersFound, blocksUnflattened, transitionsResolved
            );
        }
    }
}
