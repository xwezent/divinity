package com.divinity.deobf;

import com.divinity.cfg.BasicBlock;
import com.divinity.cfg.ControlFlowGraph;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import com.divinity.deobf.mba.MbaSolver;
import com.divinity.deobf.mba.EnhancedMbaSolver;
import com.divinity.deobf.controlflow.ControlFlowUnflattener;
import java.util.*;

public final class DeobfuscationPipeline {

    private final ControlFlowGraph cfg;
    private final List<DeobfuscationPass> passes;
    private final boolean aggressive;

    public DeobfuscationPipeline(ControlFlowGraph cfg) {
        this(cfg, true);
    }

    public DeobfuscationPipeline(ControlFlowGraph cfg, boolean aggressive) {
        this.cfg = cfg;
        this.passes = new ArrayList<>();
        this.aggressive = aggressive;
        initPasses();
    }

    private void initPasses() {
        passes.add(new NopRemovePass());
        passes.add(new DeadCodeEliminationPass());

        if (aggressive) {
            passes.add(new ControlFlowUnflatteningPass());
        }

        passes.add(new GotoChainCollapsePass());

        if (aggressive) {
            passes.add(new EnhancedMbaSimplifyPass());
        } else {
            passes.add(new MbaSimplifyPass());
        }

        passes.add(new ConstantFoldPass());
        passes.add(new DeadCodeEliminationPass());

        if (aggressive) {
            passes.add(new EnhancedMbaSimplifyPass());
        } else {
            passes.add(new MbaSimplifyPass());
        }

        passes.add(new OpaquePredicatePass());
        passes.add(new DeadCodeEliminationPass());
    }

    public void run() {
        for (DeobfuscationPass pass : passes) {
            try {
                pass.run(cfg);
            } catch (Exception e) {
            }
        }
    }

    private interface DeobfuscationPass {
        void run(ControlFlowGraph cfg);
    }

    private static class NopRemovePass implements DeobfuscationPass {
        @Override
        public void run(ControlFlowGraph cfg) {
            cfg.removeNopInstructions();
        }
    }

    private static class ControlFlowUnflatteningPass implements DeobfuscationPass {
        @Override
        public void run(ControlFlowGraph cfg) {
            try {
                ControlFlowUnflattener unflattener = new ControlFlowUnflattener(cfg);
                unflattener.unflatten();
            } catch (Exception e) {
            }
        }
    }

    private static class EnhancedMbaSimplifyPass implements DeobfuscationPass {
        @Override
        public void run(ControlFlowGraph cfg) {
            EnhancedMbaSolver solver = new EnhancedMbaSolver();
            for (BasicBlock block : cfg.getBlocks()) {
                simplifyMbaInBlock(block, solver);
            }
        }

        private void simplifyMbaInBlock(BasicBlock block, EnhancedMbaSolver solver) {
            List<Instruction> insns = block.instructions;

            for (int i = 0; i < insns.size(); i++) {
                Instruction inst = insns.get(i);

                if (inst.opcode == Opcode.IAND || inst.opcode == Opcode.IOR
                        || inst.opcode == Opcode.IXOR || inst.opcode == Opcode.ISUB
                        || inst.opcode == Opcode.IADD) {

                    List<Instruction> window = new ArrayList<>();
                    for (int j = Math.max(0, i - 5); j <= Math.min(insns.size() - 1, i + 2); j++) {
                        window.add(insns.get(j));
                    }

                    trySimplifyWindow(window, block, insns, i, solver);
                }
            }
        }

        private void trySimplifyWindow(List<Instruction> window, BasicBlock block,
                                        List<Instruction> insns, int currentIdx,
                                        EnhancedMbaSolver solver) {
        }
    }

    private static class GotoChainCollapsePass implements DeobfuscationPass {
        @Override
        public void run(ControlFlowGraph cfg) {
            cfg.collapseGotoChains();
        }
    }

    private static class DeadCodeEliminationPass implements DeobfuscationPass {
        @Override
        public void run(ControlFlowGraph cfg) {
            Set<BasicBlock> reachable = new LinkedHashSet<>();
            ArrayDeque<BasicBlock> queue = new ArrayDeque<>();
            BasicBlock entry = cfg.getEntryBlock();
            if (entry != null) {
                queue.add(entry);
                while (!queue.isEmpty()) {
                    BasicBlock block = queue.poll();
                    if (!reachable.add(block)) continue;
                    queue.addAll(block.successors);
                }
            }

            List<BasicBlock> allBlocks = new ArrayList<>(cfg.getBlocks());
            for (BasicBlock block : allBlocks) {
                if (!reachable.contains(block)) {
                    block.instructions.clear();
                    block.successors.clear();
                    for (BasicBlock pred : block.predecessors) {
                        pred.successors.remove(block);
                    }
                    block.predecessors.clear();
                }
            }
        }
    }

    private static class MbaSimplifyPass implements DeobfuscationPass {
        @Override
        public void run(ControlFlowGraph cfg) {
            for (BasicBlock block : cfg.getBlocks()) {
                simplifyMbaInBlock(block);
            }
        }

        private void simplifyMbaInBlock(BasicBlock block) {
            List<Instruction> insns = block.instructions;

            for (int i = 0; i < insns.size(); i++) {
                Instruction inst = insns.get(i);

                if (inst.opcode == Opcode.IAND || inst.opcode == Opcode.IOR
                        || inst.opcode == Opcode.IXOR || inst.opcode == Opcode.ISUB
                        || inst.opcode == Opcode.IADD) {

                    List<Instruction> window = new ArrayList<>();
                    for (int j = Math.max(0, i - 3); j <= Math.min(insns.size() - 1, i + 1); j++) {
                        window.add(insns.get(j));
                    }

                    trySimplifyWindow(window, block, insns, i);
                }

                if (inst.opcode == Opcode.GOTO && i + 1 < insns.size()) {
                    Instruction next = insns.get(i + 1);
                    if (next.opcode == Opcode.ICONST_0 || next.opcode == Opcode.ICONST_1
                            || next.opcode.isConstantLoad()) {
                        insns.remove(i);
                        i--;
                    }
                }
            }
        }

        private void trySimplifyWindow(List<Instruction> window, BasicBlock block,
                                        List<Instruction> insns, int currentIdx) {

            if (window.size() < 3) return;

            Instruction inst = insns.get(currentIdx);

            if (inst.opcode == Opcode.IAND) {
                int notCount = 0;
                int xorCount = 0;
                for (int w = 0; w < window.size(); w++) {
                    if (window.get(w).opcode == Opcode.ICONST_M1) notCount++;
                    if (window.get(w).opcode == Opcode.IXOR) xorCount++;
                }

                if (notCount >= 1 && xorCount >= 1) {

                }
            }
        }
    }

    private static class ConstantFoldPass implements DeobfuscationPass {
        @Override
        public void run(ControlFlowGraph cfg) {
            for (BasicBlock block : cfg.getBlocks()) {
                foldConstantsInBlock(block);
            }
        }

        private void foldConstantsInBlock(BasicBlock block) {
            List<Instruction> insns = block.instructions;
            for (int i = 0; i < insns.size() - 2; i++) {
                Instruction a = insns.get(i);
                Instruction b = insns.get(i + 1);
                Instruction op = insns.get(i + 2);

                Integer lVal = constantValue(a);
                Integer rVal = constantValue(b);

                if (lVal == null || rVal == null) continue;

                Integer result = switch (op.opcode) {
                    case IADD -> lVal + rVal;
                    case ISUB -> lVal - rVal;
                    case IMUL -> lVal * rVal;
                    case IDIV -> rVal != 0 ? lVal / rVal : null;
                    case IREM -> rVal != 0 ? lVal % rVal : null;
                    case IAND -> lVal & rVal;
                    case IOR -> lVal | rVal;
                    case IXOR -> lVal ^ rVal;
                    case ISHL -> lVal << rVal;
                    case ISHR -> lVal >> rVal;
                    case IUSHR -> lVal >>> rVal;
                    default -> null;
                };

                if (result != null) {
                    Instruction replacement = makeConstantLoad(result);
                    insns.set(i, replacement);
                    insns.remove(i + 2);
                    insns.remove(i + 1);
                    i--;
                }
            }
        }

        private Integer constantValue(Instruction inst) {
            return switch (inst.opcode) {
                case ICONST_M1 -> -1;
                case ICONST_0 -> 0;
                case ICONST_1 -> 1;
                case ICONST_2 -> 2;
                case ICONST_3 -> 3;
                case ICONST_4 -> 4;
                case ICONST_5 -> 5;
                case BIPUSH -> (int)(byte)inst.operands[0];
                case SIPUSH -> (int)(short)((inst.operands[0] << 8) | inst.operands[1]);
                default -> null;
            };
        }

        private Instruction makeConstantLoad(int value) {
            return switch (value) {
                case -1 -> new Instruction(Opcode.ICONST_M1, -1);
                case 0 -> new Instruction(Opcode.ICONST_0, -1);
                case 1 -> new Instruction(Opcode.ICONST_1, -1);
                case 2 -> new Instruction(Opcode.ICONST_2, -1);
                case 3 -> new Instruction(Opcode.ICONST_3, -1);
                case 4 -> new Instruction(Opcode.ICONST_4, -1);
                case 5 -> new Instruction(Opcode.ICONST_5, -1);
                default -> {
                    if (value >= -128 && value <= 127) {
                        yield new Instruction(Opcode.BIPUSH, -1, value & 0xFF);
                    }
                    yield new Instruction(Opcode.SIPUSH, -1, (value >> 8) & 0xFF, value & 0xFF);
                }
            };
        }
    }

    private static class OpaquePredicatePass implements DeobfuscationPass {
        @Override
        public void run(ControlFlowGraph cfg) {
            for (BasicBlock block : cfg.getBlocks()) {
                List<Instruction> insns = block.instructions;
                if (insns.size() < 3) continue;

                for (int i = 0; i < insns.size() - 2; i++) {
                    Instruction a = insns.get(i);
                    Instruction b = insns.get(i + 1);
                    Instruction c = insns.get(i + 2);

                    if (isAlwaysTrue(a, b, c)) {
                        if (c.opcode.isConditionalBranch()) {
                            block.successors.clear();
                            int targetOffset = c.offset + (short)((c.operands[0] << 8) | c.operands[1]);
                            for (BasicBlock succ : cfg.getBlocks()) {
                                if (succ.startOffset == targetOffset) {
                                    block.successors.add(succ);
                                    succ.predecessors.clear();
                                    succ.predecessors.add(block);
                                    break;
                                }
                            }
                            Instruction replacement = new Instruction(Opcode.GOTO, c.offset, c.operands);
                            insns.remove(i);
                            insns.remove(i);
                            insns.set(i, replacement);
                            break;
                        }
                    }

                    if (isAlwaysFalse(a, b, c)) {
                        if (c.opcode.isConditionalBranch()) {
                            insns.remove(i);
                            insns.remove(i);
                            insns.remove(i);
                            break;
                        }
                    }
                }
            }
        }

        private boolean isAlwaysTrue(Instruction a, Instruction b, Instruction c) {
            if (c.opcode != Opcode.IF_ICMPEQ && c.opcode != Opcode.IF_ICMPNE) return false;
            Integer va = constantVal(a);
            Integer vb = constantVal(b);
            if (va == null || vb == null) return false;

            if (c.opcode == Opcode.IF_ICMPEQ) return va.equals(vb);
            if (c.opcode == Opcode.IF_ICMPNE) return !va.equals(vb);
            return false;
        }

        private boolean isAlwaysFalse(Instruction a, Instruction b, Instruction c) {
            if (c.opcode != Opcode.IF_ICMPEQ && c.opcode != Opcode.IF_ICMPNE) return false;
            Integer va = constantVal(a);
            Integer vb = constantVal(b);
            if (va == null || vb == null) return false;

            if (c.opcode == Opcode.IF_ICMPEQ) return !va.equals(vb);
            if (c.opcode == Opcode.IF_ICMPNE) return va.equals(vb);
            return false;
        }

        private Integer constantVal(Instruction inst) {
            return switch (inst.opcode) {
                case ICONST_M1 -> -1;
                case ICONST_0 -> 0;
                case ICONST_1 -> 1;
                case ICONST_2 -> 2;
                case ICONST_3 -> 3;
                case ICONST_4 -> 4;
                case ICONST_5 -> 5;
                default -> null;
            };
        }
    }
}
