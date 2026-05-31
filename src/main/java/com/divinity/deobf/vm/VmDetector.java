package com.divinity.deobf.vm;

import com.divinity.cfg.BasicBlock;
import com.divinity.cfg.ControlFlowGraph;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import java.util.*;

public final class VmDetector {

    private final ControlFlowGraph cfg;

    public VmDetector(ControlFlowGraph cfg) {
        this.cfg = cfg;
    }

    public VmPattern detectVm() {
        VmPattern zelixPattern = detectZelixVm();
        if (zelixPattern != null) return zelixPattern;

        VmPattern dashOPattern = detectDashOVm();
        if (dashOPattern != null) return dashOPattern;

        VmPattern genericPattern = detectGenericVm();
        if (genericPattern != null) return genericPattern;

        return null;
    }

    private VmPattern detectZelixVm() {
        for (BasicBlock block : cfg.getBlocks()) {
            if (hasZelixVmSignature(block)) {
                return new VmPattern(
                    VmPattern.VmType.ZELIX,
                    block,
                    extractVmBytecode(block),
                    extractVmContext(block)
                );
            }
        }
        return null;
    }

    private boolean hasZelixVmSignature(BasicBlock block) {
        int switchCount = 0;
        int arrayLoadCount = 0;
        int loopCount = 0;

        for (Instruction inst : block.instructions) {
            if (inst.opcode == Opcode.TABLESWITCH || inst.opcode == Opcode.LOOKUPSWITCH) {
                switchCount++;
            }
            if (inst.opcode == Opcode.BALOAD || inst.opcode == Opcode.IALOAD) {
                arrayLoadCount++;
            }
        }

        if (isLoopHeader(block)) {
            loopCount++;
        }

        return switchCount >= 1 && arrayLoadCount >= 2 && loopCount >= 1;
    }

    private VmPattern detectDashOVm() {
        for (BasicBlock block : cfg.getBlocks()) {
            if (hasDashOVmSignature(block)) {
                return new VmPattern(
                    VmPattern.VmType.DASHO,
                    block,
                    extractVmBytecode(block),
                    extractVmContext(block)
                );
            }
        }
        return null;
    }

    private boolean hasDashOVmSignature(BasicBlock block) {
        boolean hasDispatcher = false;
        boolean hasBytecodeArray = false;
        boolean hasStackManipulation = false;

        for (Instruction inst : block.instructions) {
            if (inst.opcode == Opcode.TABLESWITCH) {
                hasDispatcher = true;
            }
            if (inst.opcode == Opcode.NEWARRAY && inst.operands.length > 0 && inst.operands[0] == 8) {
                hasBytecodeArray = true;
            }
            if (inst.opcode == Opcode.DUP || inst.opcode == Opcode.SWAP) {
                hasStackManipulation = true;
            }
        }

        return hasDispatcher && hasBytecodeArray && hasStackManipulation;
    }

    private VmPattern detectGenericVm() {
        for (BasicBlock block : cfg.getBlocks()) {
            if (hasGenericVmSignature(block)) {
                return new VmPattern(
                    VmPattern.VmType.GENERIC,
                    block,
                    extractVmBytecode(block),
                    extractVmContext(block)
                );
            }
        }
        return null;
    }

    private boolean hasGenericVmSignature(BasicBlock block) {
        int complexity = computeBlockComplexity(block);
        boolean hasLoop = isLoopHeader(block);
        boolean hasSwitch = containsSwitch(block);
        boolean hasArrayOps = containsArrayOperations(block);

        return complexity > 50 && hasLoop && hasSwitch && hasArrayOps;
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

    private boolean containsSwitch(BasicBlock block) {
        for (Instruction inst : block.instructions) {
            if (inst.opcode == Opcode.TABLESWITCH || inst.opcode == Opcode.LOOKUPSWITCH) {
                return true;
            }
        }
        return false;
    }

    private boolean containsArrayOperations(BasicBlock block) {
        int arrayOps = 0;
        for (Instruction inst : block.instructions) {
            if (inst.opcode == Opcode.BALOAD || inst.opcode == Opcode.IALOAD ||
                inst.opcode == Opcode.AALOAD || inst.opcode == Opcode.BASTORE ||
                inst.opcode == Opcode.IASTORE || inst.opcode == Opcode.AASTORE) {
                arrayOps++;
            }
        }
        return arrayOps >= 3;
    }

    private int computeBlockComplexity(BasicBlock block) {
        int complexity = 0;
        complexity += block.instructions.size();
        complexity += block.successors.size() * 5;
        complexity += block.predecessors.size() * 3;

        for (Instruction inst : block.instructions) {
            if (inst.opcode == Opcode.TABLESWITCH || inst.opcode == Opcode.LOOKUPSWITCH) {
                complexity += 20;
            }
            if (inst.opcode.isConditionalBranch()) {
                complexity += 5;
            }
        }

        return complexity;
    }

    private byte[] extractVmBytecode(BasicBlock block) {
        List<Byte> bytecode = new ArrayList<>();

        for (Instruction inst : block.instructions) {
            if (inst.opcode == Opcode.BIPUSH || inst.opcode == Opcode.SIPUSH) {
                if (inst.operands.length > 0) {
                    bytecode.add((byte) inst.operands[0]);
                }
            }
        }

        byte[] result = new byte[bytecode.size()];
        for (int i = 0; i < bytecode.size(); i++) {
            result[i] = bytecode.get(i);
        }
        return result;
    }

    private Map<String, Object> extractVmContext(BasicBlock block) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("block", block);
        context.put("instructionCount", block.instructions.size());
        context.put("successorCount", block.successors.size());
        return context;
    }

    public record VmPattern(VmType type, BasicBlock vmBlock, byte[] bytecode,
                            Map<String, Object> context) {
        public enum VmType {
            ZELIX,
            DASHO,
            GENERIC,
            UNKNOWN
        }
    }
}
