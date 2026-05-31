package com.divinity.bytecode;

import com.divinity.classfile.ClassFileParser;
import com.divinity.classfile.ConstantPool;
import com.divinity.util.IoUtil;
import java.nio.ByteBuffer;
import java.util.*;

public final class InstructionDecoder {

    private final ClassFileParser classParser;

    public InstructionDecoder(ClassFileParser classParser) {
        this.classParser = classParser;
    }

    public List<Instruction> decode(byte[] bytecode) {
        List<Instruction> instructions = new ArrayList<>();
        ByteBuffer buf = IoUtil.buffer(bytecode);
        Map<Integer, Instruction> byOffset = new LinkedHashMap<>();

        while (buf.hasRemaining()) {
            int offset = buf.position();
            int b = IoUtil.readU1(buf);
            Opcode op = Opcode.fromByte(b);

            if (op == null) {
                instructions.add(new Instruction(Opcode.NOP, offset));
                continue;
            }

            int[] operands;
            if (op.operandSize >= 0) {
                operands = new int[op.operandSize];
                for (int i = 0; i < op.operandSize; i++) {
                    operands[i] = IoUtil.readU1(buf);
                }
            } else if (op == Opcode.TABLESWITCH || op == Opcode.LOOKUPSWITCH) {
                operands = parseSwitch(buf, offset, op);
            } else if (op == Opcode.WIDE) {
                int wideOp = IoUtil.readU1(buf);
                if (wideOp == 0x84) {
                    operands = new int[]{wideOp, IoUtil.readU2(buf), IoUtil.readS2(buf)};
                } else {
                    operands = new int[]{wideOp, IoUtil.readU2(buf)};
                }
            } else {
                operands = new int[0];
            }

            Instruction inst = new Instruction(op, offset, operands);
            instructions.add(inst);
            byOffset.put(offset, inst);
        }

        buildEdges(instructions, byOffset);

        return instructions;
    }

    private int[] parseSwitch(ByteBuffer buf, int offset, Opcode op) {
        int padding = (4 - (offset + 1) % 4) % 4;
        buf.position(buf.position() + padding);

        if (op == Opcode.TABLESWITCH) {
            int def = buf.getInt();
            int low = buf.getInt();
            int high = buf.getInt();
            int count = high - low + 1;
            if (count < 0 || count > 100000) count = 0;
            int[] operands = new int[3 + count];
            operands[0] = def;
            operands[1] = low;
            operands[2] = high;
            for (int i = 0; i < count; i++) {
                operands[3 + i] = buf.getInt();
            }
            return operands;
        } else {
            int def = buf.getInt();
            int npairs = buf.getInt();
            if (npairs < 0 || npairs > 100000) npairs = 0;
            int[] operands = new int[2 + npairs * 2];
            operands[0] = def;
            operands[1] = npairs;
            for (int i = 0; i < npairs; i++) {
                operands[2 + i * 2] = buf.getInt();
                operands[2 + i * 2 + 1] = buf.getInt();
            }
            return operands;
        }
    }

    private void buildEdges(List<Instruction> instructions, Map<Integer, Instruction> byOffset) {
        for (int i = 0; i < instructions.size(); i++) {
            Instruction inst = instructions.get(i);

            if (inst.opcode.isBranch()) {
                int[] targets = getBranchTargets(inst);
                for (int target : targets) {
                    Instruction succ = byOffset.get(target);
                    if (succ != null) {
                        inst.successors.add(succ);
                        succ.predecessors.add(inst);
                    }
                }
            }

            if (inst.opcode == Opcode.RET) {
                continue;
            }

            if (inst.fallsThrough() && i + 1 < instructions.size()) {
                Instruction next = instructions.get(i + 1);
                inst.successors.add(next);
                next.predecessors.add(inst);
            }

            if (inst.opcode.isReturn() || inst.opcode == Opcode.ATHROW) {
                continue;
            }

            if (inst.opcode == Opcode.TABLESWITCH || inst.opcode == Opcode.LOOKUPSWITCH) {
                if (i + 1 < instructions.size()) {
                    Instruction next = instructions.get(i + 1);
                    if (!inst.successors.contains(next)) {
                        inst.successors.add(next);
                        next.predecessors.add(inst);
                    }
                }
            }
        }
    }

    private int[] getBranchTargets(Instruction inst) {
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
            case JSR, JSR_W -> {
                int branch;
                if (inst.opcode == Opcode.JSR) {
                    branch = offset + (short) ((inst.operands[0] << 8) | inst.operands[1]);
                } else {
                    branch = offset + ((inst.operands[0] << 24) | (inst.operands[1] << 16)
                            | (inst.operands[2] << 8) | inst.operands[3]);
                }
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
                int[] targets = new int[inst.operands[1] + 1];
                targets[0] = inst.operands[0];
                for (int i = 0; i < inst.operands[1]; i++) {
                    targets[i + 1] = inst.operands[2 + i * 2 + 1];
                }
                yield targets;
            }
            default -> new int[0];
        };
    }
}
