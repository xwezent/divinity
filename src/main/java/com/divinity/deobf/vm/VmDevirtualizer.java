package com.divinity.deobf.vm;

import com.divinity.cfg.ControlFlowGraph;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import java.util.*;

public final class VmDevirtualizer {

    private final ControlFlowGraph cfg;
    private final VmDetector detector;

    public VmDevirtualizer(ControlFlowGraph cfg) {
        this.cfg = cfg;
        this.detector = new VmDetector(cfg);
    }

    public boolean devirtualize() {
        VmDetector.VmPattern pattern = detector.detectVm();
        if (pattern == null) return false;

        return switch (pattern.type()) {
            case ZELIX -> devirtualizeZelix(pattern);
            case DASHO -> devirtualizeDashO(pattern);
            case GENERIC -> devirtualizeGeneric(pattern);
            default -> false;
        };
    }

    private boolean devirtualizeZelix(VmDetector.VmPattern pattern) {
        try {
            VmInstructionSet instructionSet = VmInstructionSet.zelixInstructionSet();
            List<Instruction> translated = translateVmBytecode(
                pattern.bytecode(), instructionSet);

            if (translated != null && !translated.isEmpty()) {
                replaceVmCode(pattern.vmBlock(), translated);
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    private boolean devirtualizeDashO(VmDetector.VmPattern pattern) {
        try {
            VmInstructionSet instructionSet = VmInstructionSet.dashOInstructionSet();
            List<Instruction> translated = translateVmBytecode(
                pattern.bytecode(), instructionSet);

            if (translated != null && !translated.isEmpty()) {
                replaceVmCode(pattern.vmBlock(), translated);
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    private boolean devirtualizeGeneric(VmDetector.VmPattern pattern) {
        try {
            VmInstructionSet instructionSet = inferInstructionSet(pattern);
            if (instructionSet == null) return false;

            List<Instruction> translated = translateVmBytecode(
                pattern.bytecode(), instructionSet);

            if (translated != null && !translated.isEmpty()) {
                replaceVmCode(pattern.vmBlock(), translated);
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    private List<Instruction> translateVmBytecode(byte[] bytecode, VmInstructionSet instructionSet) {
        List<Instruction> result = new ArrayList<>();
        int pc = 0;

        while (pc < bytecode.length) {
            int opcode = bytecode[pc] & 0xFF;
            VmInstructionSet.VmInstruction vmInst = instructionSet.getInstruction(opcode);

            if (vmInst == null) {
                pc++;
                continue;
            }

            int[] operands = new int[vmInst.operandCount()];
            for (int i = 0; i < vmInst.operandCount() && pc + 1 + i < bytecode.length; i++) {
                operands[i] = bytecode[pc + 1 + i] & 0xFF;
            }

            Instruction javaInst = vmInst.toJavaInstruction(operands);
            if (javaInst != null) {
                result.add(javaInst);
            }

            pc += 1 + vmInst.operandCount();
        }

        return result;
    }

    private VmInstructionSet inferInstructionSet(VmDetector.VmPattern pattern) {
        Map<Integer, Integer> opcodeFrequency = new LinkedHashMap<>();

        for (byte b : pattern.bytecode()) {
            int opcode = b & 0xFF;
            opcodeFrequency.put(opcode, opcodeFrequency.getOrDefault(opcode, 0) + 1);
        }

        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(opcodeFrequency.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        VmInstructionSet instructionSet = new VmInstructionSet();

        if (sorted.size() >= 10) {
            instructionSet.addInstruction(sorted.get(0).getKey(), "PUSH", 1,
                operands -> new Instruction(Opcode.BIPUSH, -1, operands[0]));
            instructionSet.addInstruction(sorted.get(1).getKey(), "LOAD", 1,
                operands -> new Instruction(Opcode.ILOAD, -1, operands[0]));
            instructionSet.addInstruction(sorted.get(2).getKey(), "STORE", 1,
                operands -> new Instruction(Opcode.ISTORE, -1, operands[0]));
            instructionSet.addInstruction(sorted.get(3).getKey(), "ADD", 0,
                operands -> new Instruction(Opcode.IADD, -1));
            instructionSet.addInstruction(sorted.get(4).getKey(), "SUB", 0,
                operands -> new Instruction(Opcode.ISUB, -1));
        }

        return instructionSet;
    }

    private void replaceVmCode(com.divinity.cfg.BasicBlock vmBlock, List<Instruction> translated) {
        vmBlock.instructions.clear();
        vmBlock.instructions.addAll(translated);
    }
}
