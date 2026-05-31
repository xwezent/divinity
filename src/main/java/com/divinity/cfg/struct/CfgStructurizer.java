package com.divinity.cfg.struct;

import com.divinity.cfg.BasicBlock;
import com.divinity.cfg.ControlFlowGraph;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import java.util.*;

public final class CfgStructurizer {

    public sealed interface Region {
        record BlockRegion(BasicBlock block) implements Region {}
        record SequenceRegion(List<Region> children) implements Region {}
        record IfRegion(Region condition, Region thenBranch, Region elseBranch) implements Region {}
        record WhileRegion(Region condition, Region body, boolean isDoWhile) implements Region {}
        record SwitchRegion(Region selector, List<CaseEntry> cases, Region defaultCase) implements Region {}
        record TryCatchRegion(Region tryBody, List<CatchEntry> catches, Region finallyBody) implements Region {}
        record InfiniteLoopRegion(Region body) implements Region {}
        record ReturnRegion() implements Region {}
        record ThrowRegion() implements Region {}
        record BreakRegion() implements Region {}
        record ContinueRegion() implements Region {}
    }

    public record CaseEntry(List<Integer> keys, Region body) {}
    public record CatchEntry(String exceptionType, String varName, Region body) {}

    private final ControlFlowGraph cfg;
    private final Map<BasicBlock, Region> blockToRegion;
    private final Map<BasicBlock, RegionNode> nodes;

    public CfgStructurizer(ControlFlowGraph cfg) {
        this.cfg = cfg;
        this.blockToRegion = new HashMap<>();
        this.nodes = new LinkedHashMap<>();
    }

    private static class RegionNode {
        BasicBlock block;
        RegionNode immediatePostDominator;
        final List<RegionNode> successors;
        final List<RegionNode> predecessors;
        boolean visited;

        RegionNode(BasicBlock block) {
            this.block = block;
            this.successors = new ArrayList<>();
            this.predecessors = new ArrayList<>();
        }
    }

    public Region structure() {
        if (cfg.getBlocks().isEmpty()) return new Region.ReturnRegion();

        buildNodes();
        computePostDominators();

        Region result = structureRegion(cfg.getEntryBlock(), new HashSet<>());
        return result != null ? result : new Region.ReturnRegion();
    }

    private void buildNodes() {
        List<BasicBlock> blocks = cfg.getBlocks();
        Map<BasicBlock, RegionNode> blockNodeMap = new HashMap<>();
        for (BasicBlock block : blocks) {
            RegionNode node = new RegionNode(block);
            blockNodeMap.put(block, node);
            nodes.put(block, node);
        }

        for (BasicBlock block : blocks) {
            RegionNode node = nodes.get(block);
            for (BasicBlock succ : block.successors) {
                RegionNode succNode = nodes.get(succ);
                if (succNode != null) {
                    node.successors.add(succNode);
                    succNode.predecessors.add(node);
                }
            }
        }
    }

    private void computePostDominators() {
        List<BasicBlock> blocks = cfg.getBlocks();
        int n = blocks.size();
        if (n == 0) return;

        Set<BasicBlock> exits = new HashSet<>();
        for (BasicBlock block : blocks) {
            if (block.successors.isEmpty() || block.isReturnBlock() || block.isThrowBlock()) {
                exits.add(block);
            }
        }

        Map<BasicBlock, Set<BasicBlock>> pdom = new HashMap<>();
        for (BasicBlock block : blocks) {
            pdom.put(block, new HashSet<>(blocks));
        }

        for (BasicBlock exit : exits) {
            pdom.put(exit, Set.of(exit));
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (BasicBlock block : blocks) {
                if (exits.contains(block)) continue;

                Set<BasicBlock> intersection = null;
                for (BasicBlock succ : block.successors) {
                    Set<BasicBlock> succPdom = pdom.get(succ);
                    if (intersection == null) {
                        intersection = new HashSet<>(succPdom);
                    } else {
                        intersection.retainAll(succPdom);
                    }
                }

                if (intersection == null) intersection = new HashSet<>();
                intersection.add(block);

                if (!intersection.equals(pdom.get(block))) {
                    pdom.put(block, intersection);
                    changed = true;
                }
            }
        }

        for (BasicBlock block : blocks) {
            RegionNode node = nodes.get(block);
            if (node == null) continue;

            Set<BasicBlock> blockPdom = pdom.get(block);
            blockPdom.remove(block);

            Set<BasicBlock> candidates = new HashSet<>(blockPdom);
            for (BasicBlock c1 : blockPdom) {
                for (BasicBlock c2 : blockPdom) {
                    if (c1 != c2 && pdom.get(c2).contains(c1)) {
                        candidates.remove(c2);
                    }
                }
            }

            if (candidates.size() == 1) {
                BasicBlock ipdom = candidates.iterator().next();
                node.immediatePostDominator = nodes.get(ipdom);
            }
        }
    }

    private Region structureRegion(BasicBlock block, Set<BasicBlock> visited) {
        if (block == null) return new Region.ReturnRegion();

        if (block.isReturnBlock()) return new Region.ReturnRegion();
        if (block.isThrowBlock()) return new Region.ThrowRegion();

        BasicBlock follow = findFollow(block);

        List<BasicBlock> body = new ArrayList<>();
        BasicBlock current = block;
        Set<BasicBlock> bodyVisited = new HashSet<>();

        while (current != null) {
            if (current == follow) break;
            if (!bodyVisited.add(current)) break;

            if (isLoopHeader(current)) {
                List<BasicBlock> loopBlocks = collectLoop(current, follow);
                Region loop = structureLoop(current, loopBlocks, follow);
                body.add(current);
                current = findFollow(current);
                continue;
            }

            if (isConditional(current)) {
                BasicBlock nextInBody = getNextInBody(current, follow, bodyVisited);
                if (nextInBody != null && current.successors.contains(nextInBody)) {
                    body.add(current);
                    current = nextInBody;
                } else {
                    body.add(current);
                    break;
                }
            } else {
                body.add(current);
                if (current.successors.size() == 1) {
                    current = current.successors.get(0);
                } else {
                    break;
                }
            }
        }

        if (body.size() == 1) {
            BasicBlock bb = body.get(0);
            if (isConditional(bb)) {
                BasicBlock thenBlock = bb.successors.isEmpty() ? null : bb.successors.get(0);
                BasicBlock elseBlock = null;

                if (thenBlock != null) {
                    if (follow != null && thenBlock.successors.contains(follow)) {
                        elseBlock = null;
                    } else if (bb.successors.size() > 1) {
                        elseBlock = bb.successors.get(1);
                    } else {
                        thenBlock = bb.successors.get(0);
                    }
                }

                if (thenBlock != null && follow != null && thenBlock.successors.contains(follow)) {
                    return new Region.IfRegion(
                            new Region.BlockRegion(bb),
                            new Region.BlockRegion(thenBlock),
                            new Region.ReturnRegion());
                }
            }
        }

        List<Region> regions = new ArrayList<>();
        for (BasicBlock b : body) {
            regions.add(new Region.BlockRegion(b));
        }

        if (regions.size() == 1) return regions.get(0);
        return new Region.SequenceRegion(regions);
    }

    private BasicBlock getNextInBody(BasicBlock block, BasicBlock follow, Set<BasicBlock> visited) {
        for (BasicBlock succ : block.successors) {
            if (succ == follow) continue;
            if (!visited.contains(succ)) return succ;
        }
        return block.successors.isEmpty() ? null : block.successors.get(0);
    }

    private BasicBlock findFollow(BasicBlock block) {
        RegionNode node = nodes.get(block);
        if (node == null || node.immediatePostDominator == null) {
            for (BasicBlock succ : block.successors) {
                if (succ.isReturnBlock() || succ.isThrowBlock()) return succ;
            }
            return null;
        }
        return node.immediatePostDominator.block;
    }

    private boolean isLoopHeader(BasicBlock block) {
        for (BasicBlock pred : block.predecessors) {
            if (dominates(block, pred)) return true;
        }
        return false;
    }

    private boolean dominates(BasicBlock a, BasicBlock b) {
        if (a == b) return false;
        BasicBlock current = b;
        Set<BasicBlock> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            if (current == a) return true;
            if (current.immediateDominator == null) break;
            current = current.immediateDominator;
        }
        return false;
    }

    private List<BasicBlock> collectLoop(BasicBlock header, BasicBlock follow) {
        List<BasicBlock> loop = new ArrayList<>();
        Set<BasicBlock> visited = new HashSet<>();
        Deque<BasicBlock> queue = new ArrayDeque<>();
        queue.add(header);

        while (!queue.isEmpty()) {
            BasicBlock block = queue.poll();
            if (block == follow) continue;
            if (!visited.add(block)) continue;
            loop.add(block);
            for (BasicBlock succ : block.successors) {
                if (succ != follow && !visited.contains(succ)) {
                    queue.add(succ);
                }
            }
        }
        return loop;
    }

    private Region structureLoop(BasicBlock header, List<BasicBlock> loopBlocks, BasicBlock follow) {
        boolean isDoWhile = false;
        BasicBlock conditionBlock = null;
        BasicBlock bodyBlock = null;

        for (BasicBlock block : loopBlocks) {
            if (block.predecessors.contains(header) && block.successors.contains(header)) {
                conditionBlock = block;
            }
        }
        if (conditionBlock == null) conditionBlock = header;

        List<Region> bodyRegions = new ArrayList<>();
        for (BasicBlock block : loopBlocks) {
            if (block == conditionBlock) continue;
            bodyRegions.add(new Region.BlockRegion(block));
        }

        Region condition = new Region.BlockRegion(conditionBlock);
        Region body = bodyRegions.isEmpty()
                ? new Region.SequenceRegion(List.of())
                : bodyRegions.size() == 1 ? bodyRegions.get(0) : new Region.SequenceRegion(bodyRegions);

        BasicBlock condLast = conditionBlock;
        if (condLast != null && condLast.successors.contains(header)) {
            isDoWhile = condLast.predecessors.contains(header);
        }

        return new Region.WhileRegion(condition, body, isDoWhile);
    }

    private boolean isConditional(BasicBlock block) {
        Instruction last = block.lastInstruction();
        return last != null && last.opcode.isConditionalBranch();
    }

    public static String regionToJava(Region region, ControlFlowGraph cfg) {
        if (region instanceof Region.BlockRegion br) {
            return writeBlock(br.block(), cfg);
        }
        if (region instanceof Region.SequenceRegion sr) {
            StringBuilder sb = new StringBuilder();
            for (Region child : sr.children()) {
                sb.append(regionToJava(child, cfg));
            }
            return sb.toString();
        }
        if (region instanceof Region.IfRegion ir) {
            StringBuilder sb = new StringBuilder();
            sb.append(writeBlock(ir.condition() instanceof Region.BlockRegion bc ? bc.block() : null, cfg));
            sb.append("if (/* condition */) {\n");
            sb.append(indent(regionToJava(ir.thenBranch(), cfg)));
            sb.append("}\n");
            if (ir.elseBranch() != null && !(ir.elseBranch() instanceof Region.ReturnRegion)) {
                sb.append(" else {\n");
                sb.append(indent(regionToJava(ir.elseBranch(), cfg)));
                sb.append("}\n");
            }
            return sb.toString();
        }
        if (region instanceof Region.WhileRegion wr) {
            StringBuilder sb = new StringBuilder();
            if (wr.isDoWhile()) {
                sb.append("do {\n");
                sb.append(indent(regionToJava(wr.body(), cfg)));
                sb.append("} while (");
                if (wr.condition() instanceof Region.BlockRegion bc) {
                    sb.append("/* condition */");
                }
                sb.append(");\n");
            } else {
                sb.append("while (");
                if (wr.condition() instanceof Region.BlockRegion bc) {
                    sb.append("/* condition */");
                }
                sb.append(") {\n");
                sb.append(indent(regionToJava(wr.body(), cfg)));
                sb.append("}\n");
            }
            return sb.toString();
        }
        if (region instanceof Region.ReturnRegion) {
            return "return;\n";
        }
        return "// region\n";
    }

    private static String writeBlock(BasicBlock block, ControlFlowGraph cfg) {
        if (block == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Instruction inst : block.instructions) {
            sb.append("    ").append(inst.toString()).append("\n");
        }
        return sb.toString();
    }

    private static String indent(String s) {
        if (s == null) return "";
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (!line.isEmpty()) sb.append("    ").append(line).append("\n");
        }
        return sb.toString();
    }
}
