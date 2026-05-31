package com.divinity.deobf.controlflow;

import com.divinity.cfg.BasicBlock;
import com.divinity.cfg.ControlFlowGraph;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import java.util.*;

public final class ControlFlowUnflattener {

    private final ControlFlowGraph cfg;

    public ControlFlowUnflattener(ControlFlowGraph cfg) {
        this.cfg = cfg;
    }

    public boolean unflatten() {
        DispatcherPattern pattern = detectDispatcherPattern();
        if (pattern == null) return false;

        StateTransitionGraph stg = buildStateTransitionGraph(pattern);
        if (stg == null) return false;

        reconstructOriginalCfg(pattern, stg);
        return true;
    }

    private DispatcherPattern detectDispatcherPattern() {
        for (BasicBlock block : cfg.getBlocks()) {
            if (isLoopHeader(block)) {
                BasicBlock loopBody = findLoopBody(block);
                if (loopBody != null && containsSwitch(loopBody)) {
                    Integer dispatcherVar = findDispatcherVariable(loopBody);
                    if (dispatcherVar != null) {
                        List<BasicBlock> switchCases = extractSwitchCases(loopBody);
                        return new DispatcherPattern(block, loopBody, dispatcherVar, switchCases);
                    }
                }
            }
        }
        return null;
    }

    private boolean isLoopHeader(BasicBlock block) {
        for (BasicBlock pred : block.predecessors) {
            if (dominates(block, pred)) {
                return true;
            }
        }
        return false;
    }

    private boolean dominates(BasicBlock a, BasicBlock b) {
        if (a == b) return false;
        BasicBlock current = b;
        Set<BasicBlock> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (current == a) return true;
            current = current.immediateDominator;
        }
        return false;
    }

    private BasicBlock findLoopBody(BasicBlock header) {
        for (BasicBlock succ : header.successors) {
            if (succ != header) {
                return succ;
            }
        }
        return header.successors.isEmpty() ? null : header.successors.get(0);
    }

    private boolean containsSwitch(BasicBlock block) {
        for (Instruction inst : block.instructions) {
            if (inst.opcode == Opcode.TABLESWITCH || inst.opcode == Opcode.LOOKUPSWITCH) {
                return true;
            }
        }
        return false;
    }

    private Integer findDispatcherVariable(BasicBlock block) {
        for (Instruction inst : block.instructions) {
            if (inst.opcode == Opcode.TABLESWITCH || inst.opcode == Opcode.LOOKUPSWITCH) {
                for (int i = block.instructions.indexOf(inst) - 1; i >= 0; i--) {
                    Instruction prev = block.instructions.get(i);
                    if (prev.opcode == Opcode.ILOAD || prev.opcode == Opcode.ILOAD_0 ||
                        prev.opcode == Opcode.ILOAD_1 || prev.opcode == Opcode.ILOAD_2 ||
                        prev.opcode == Opcode.ILOAD_3) {
                        return getVarIndex(prev);
                    }
                }
            }
        }
        return null;
    }

    private int getVarIndex(Instruction inst) {
        return switch (inst.opcode) {
            case ILOAD -> inst.operands.length > 0 ? inst.operands[0] & 0xFF : 0;
            case ILOAD_0 -> 0;
            case ILOAD_1 -> 1;
            case ILOAD_2 -> 2;
            case ILOAD_3 -> 3;
            default -> 0;
        };
    }

    private List<BasicBlock> extractSwitchCases(BasicBlock switchBlock) {
        List<BasicBlock> cases = new ArrayList<>();
        for (BasicBlock succ : switchBlock.successors) {
            if (succ != switchBlock) {
                cases.add(succ);
            }
        }
        return cases;
    }

    private StateTransitionGraph buildStateTransitionGraph(DispatcherPattern pattern) {
        StateTransitionGraph stg = new StateTransitionGraph();
        SymbolicExecutor executor = new SymbolicExecutor();

        for (BasicBlock caseBlock : pattern.switchCases()) {
            Integer entryState = findEntryState(caseBlock, pattern);
            if (entryState == null) continue;

            SymbolicExecutor.SymbolicState state = executor.execute(caseBlock, pattern.dispatcherVar());
            Integer exitState = state.getDispatcherValue();

            if (exitState != null) {
                stg.addTransition(entryState, exitState, caseBlock);
            } else {
                stg.addTransition(entryState, -1, caseBlock);
            }
        }

        return stg;
    }

    private Integer findEntryState(BasicBlock caseBlock, DispatcherPattern pattern) {
        for (BasicBlock pred : caseBlock.predecessors) {
            if (pred == pattern.switchBlock()) {
                for (Instruction inst : pred.instructions) {
                    if (inst.opcode == Opcode.TABLESWITCH) {
                        int idx = pred.successors.indexOf(caseBlock);
                        if (idx >= 0) {
                            return idx;
                        }
                    } else if (inst.opcode == Opcode.LOOKUPSWITCH) {
                        int idx = pred.successors.indexOf(caseBlock);
                        if (idx >= 0 && inst.operands.length > idx * 2 + 1) {
                            return (inst.operands[idx * 2] << 24) |
                                   (inst.operands[idx * 2 + 1] << 16) |
                                   (inst.operands[idx * 2 + 2] << 8) |
                                   inst.operands[idx * 2 + 3];
                        }
                    }
                }
            }
        }
        return null;
    }

    private void reconstructOriginalCfg(DispatcherPattern pattern, StateTransitionGraph stg) {
        Map<Integer, BasicBlock> stateToBlock = new LinkedHashMap<>();
        for (Map.Entry<Integer, StateTransitionGraph.Transition> entry : stg.transitions().entrySet()) {
            stateToBlock.put(entry.getKey(), entry.getValue().block());
        }

        for (Map.Entry<Integer, StateTransitionGraph.Transition> entry : stg.transitions().entrySet()) {
            Integer fromState = entry.getKey();
            StateTransitionGraph.Transition trans = entry.getValue();
            BasicBlock fromBlock = trans.block();
            Integer toState = trans.nextState();

            fromBlock.successors.clear();

            if (toState != null && toState != -1) {
                BasicBlock toBlock = stateToBlock.get(toState);
                if (toBlock != null) {
                    fromBlock.successors.add(toBlock);
                    if (!toBlock.predecessors.contains(fromBlock)) {
                        toBlock.predecessors.add(fromBlock);
                    }
                }
            }

            removeDispatcherUpdates(fromBlock, pattern.dispatcherVar());
        }

        pattern.loopHeader().successors.clear();
        if (!stateToBlock.isEmpty()) {
            BasicBlock firstBlock = stateToBlock.get(0);
            if (firstBlock != null) {
                pattern.loopHeader().successors.add(firstBlock);
            }
        }
    }

    private void removeDispatcherUpdates(BasicBlock block, int dispatcherVar) {
        List<Instruction> toRemove = new ArrayList<>();
        for (int i = 0; i < block.instructions.size(); i++) {
            Instruction inst = block.instructions.get(i);
            if (inst.opcode == Opcode.ISTORE || inst.opcode == Opcode.ISTORE_0 ||
                inst.opcode == Opcode.ISTORE_1 || inst.opcode == Opcode.ISTORE_2 ||
                inst.opcode == Opcode.ISTORE_3) {
                int varIdx = getVarIndex(inst);
                if (varIdx == dispatcherVar) {
                    toRemove.add(inst);
                    if (i > 0) {
                        Instruction prev = block.instructions.get(i - 1);
                        if (prev.opcode.isConstantLoad()) {
                            toRemove.add(prev);
                        }
                    }
                }
            }
        }
        block.instructions.removeAll(toRemove);
    }

    public record DispatcherPattern(BasicBlock loopHeader, BasicBlock switchBlock,
                                     int dispatcherVar, List<BasicBlock> switchCases) {}
}
