package com.divinity.ssa;

import java.util.*;

public final class SsaOptimizer {

    private final SsaForm ssaForm;
    private boolean changed;

    public SsaOptimizer(SsaForm ssaForm) {
        this.ssaForm = ssaForm;
    }

    public void optimize() {
        int iterations = 0;
        do {
            changed = false;
            constantPropagation();
            copyPropagation();
            deadCodeElimination();
            algebraicSimplification();
            iterations++;
        } while (changed && iterations < 10);
    }

    private void constantPropagation() {
        Map<SsaVariable, Object> constants = new LinkedHashMap<>();

        for (SsaBasicBlock block : ssaForm.blocks()) {
            for (SsaInstruction inst : new ArrayList<>(block.instructions())) {
                if (inst instanceof SsaInstruction.Constant c) {
                    constants.put(c.result(), c.value());
                } else if (inst instanceof SsaInstruction.BinaryOp binOp) {
                    Object leftVal = constants.get(binOp.left());
                    Object rightVal = constants.get(binOp.right());

                    if (leftVal instanceof Integer l && rightVal instanceof Integer r) {
                        Integer result = evaluateBinaryOp(binOp.operator(), l, r);
                        if (result != null) {
                            constants.put(binOp.result(), result);
                            SsaInstruction newInst = new SsaInstruction.Constant(binOp.result(), result);
                            int idx = block.instructions().indexOf(inst);
                            block.instructions().set(idx, newInst);
                            changed = true;
                        }
                    }
                } else if (inst instanceof SsaInstruction.Assign assign) {
                    Object sourceVal = constants.get(assign.source());
                    if (sourceVal != null) {
                        constants.put(assign.result(), sourceVal);
                    }
                }
            }
        }

        for (Map.Entry<SsaVariable, Object> entry : constants.entrySet()) {
            SsaVariable var = entry.getKey();
            Object value = entry.getValue();

            for (SsaInstruction use : new ArrayList<>(ssaForm.getUses(var))) {
                if (use instanceof SsaInstruction.BinaryOp binOp) {
                    if (binOp.left().equals(var) || binOp.right().equals(var)) {
                        changed = true;
                    }
                }
            }
        }
    }

    private Integer evaluateBinaryOp(String op, int left, int right) {
        return switch (op) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> right != 0 ? left / right : null;
            case "%" -> right != 0 ? left % right : null;
            case "&" -> left & right;
            case "|" -> left | right;
            case "^" -> left ^ right;
            case "<<" -> left << right;
            case ">>" -> left >> right;
            case ">>>" -> left >>> right;
            default -> null;
        };
    }

    private void copyPropagation() {
        for (SsaBasicBlock block : ssaForm.blocks()) {
            for (SsaInstruction inst : new ArrayList<>(block.instructions())) {
                if (inst instanceof SsaInstruction.Assign assign) {
                    SsaVariable target = assign.result();
                    SsaVariable source = assign.source();

                    if (!target.equals(source)) {
                        ssaForm.replaceVariable(target, source);
                        ssaForm.removeInstruction(inst, block);
                        changed = true;
                    }
                }
            }
        }
    }

    private void deadCodeElimination() {
        Set<SsaInstruction> live = new LinkedHashSet<>();
        Deque<SsaInstruction> worklist = new ArrayDeque<>();

        for (SsaBasicBlock block : ssaForm.blocks()) {
            for (SsaInstruction inst : block.instructions()) {
                if (isCritical(inst)) {
                    live.add(inst);
                    worklist.add(inst);
                }
            }
        }

        while (!worklist.isEmpty()) {
            SsaInstruction inst = worklist.poll();
            for (SsaVariable operand : inst.operands()) {
                SsaInstruction def = ssaForm.getDefinition(operand);
                if (def != null && live.add(def)) {
                    worklist.add(def);
                }
            }
        }

        for (SsaBasicBlock block : ssaForm.blocks()) {
            List<SsaInstruction> toRemove = new ArrayList<>();
            for (SsaInstruction inst : block.instructions()) {
                if (!live.contains(inst) && !isCritical(inst)) {
                    toRemove.add(inst);
                }
            }
            for (SsaInstruction inst : toRemove) {
                ssaForm.removeInstruction(inst, block);
                changed = true;
            }
        }
    }

    private boolean isCritical(SsaInstruction inst) {
        return inst instanceof SsaInstruction.Return ||
               inst instanceof SsaInstruction.Throw ||
               inst instanceof SsaInstruction.FieldStore ||
               inst instanceof SsaInstruction.ArrayStore ||
               inst instanceof SsaInstruction.Call;
    }

    private void algebraicSimplification() {
        for (SsaBasicBlock block : ssaForm.blocks()) {
            for (int i = 0; i < block.instructions().size(); i++) {
                SsaInstruction inst = block.instructions().get(i);

                if (inst instanceof SsaInstruction.BinaryOp binOp) {
                    SsaInstruction simplified = simplifyBinaryOp(binOp);
                    if (simplified != null && simplified != binOp) {
                        block.instructions().set(i, simplified);
                        changed = true;
                    }
                }
            }
        }
    }

    private SsaInstruction simplifyBinaryOp(SsaInstruction.BinaryOp binOp) {
        SsaInstruction leftDef = ssaForm.getDefinition(binOp.left());
        SsaInstruction rightDef = ssaForm.getDefinition(binOp.right());

        if (leftDef instanceof SsaInstruction.Constant lc && lc.value() instanceof Integer lv) {
            if (rightDef instanceof SsaInstruction.Constant rc && rc.value() instanceof Integer rv) {
                Integer result = evaluateBinaryOp(binOp.operator(), lv, rv);
                if (result != null) {
                    return new SsaInstruction.Constant(binOp.result(), result);
                }
            }

            return switch (binOp.operator()) {
                case "+" -> lv == 0 ? new SsaInstruction.Assign(binOp.result(), binOp.right()) : null;
                case "*" -> lv == 0 ? new SsaInstruction.Constant(binOp.result(), 0) :
                           lv == 1 ? new SsaInstruction.Assign(binOp.result(), binOp.right()) : null;
                case "|" -> lv == 0 ? new SsaInstruction.Assign(binOp.result(), binOp.right()) :
                           lv == -1 ? new SsaInstruction.Constant(binOp.result(), -1) : null;
                case "&" -> lv == 0 ? new SsaInstruction.Constant(binOp.result(), 0) :
                           lv == -1 ? new SsaInstruction.Assign(binOp.result(), binOp.right()) : null;
                case "^" -> lv == 0 ? new SsaInstruction.Assign(binOp.result(), binOp.right()) : null;
                default -> null;
            };
        }

        if (rightDef instanceof SsaInstruction.Constant rc && rc.value() instanceof Integer rv) {
            return switch (binOp.operator()) {
                case "+" -> rv == 0 ? new SsaInstruction.Assign(binOp.result(), binOp.left()) : null;
                case "-" -> rv == 0 ? new SsaInstruction.Assign(binOp.result(), binOp.left()) : null;
                case "*" -> rv == 0 ? new SsaInstruction.Constant(binOp.result(), 0) :
                           rv == 1 ? new SsaInstruction.Assign(binOp.result(), binOp.left()) : null;
                case "/" -> rv == 1 ? new SsaInstruction.Assign(binOp.result(), binOp.left()) : null;
                case "|" -> rv == 0 ? new SsaInstruction.Assign(binOp.result(), binOp.left()) :
                           rv == -1 ? new SsaInstruction.Constant(binOp.result(), -1) : null;
                case "&" -> rv == 0 ? new SsaInstruction.Constant(binOp.result(), 0) :
                           rv == -1 ? new SsaInstruction.Assign(binOp.result(), binOp.left()) : null;
                case "^" -> rv == 0 ? new SsaInstruction.Assign(binOp.result(), binOp.left()) : null;
                case "<<", ">>" , ">>>" -> rv == 0 ? new SsaInstruction.Assign(binOp.result(), binOp.left()) : null;
                default -> null;
            };
        }

        if (binOp.left().equals(binOp.right())) {
            return switch (binOp.operator()) {
                case "-" -> new SsaInstruction.Constant(binOp.result(), 0);
                case "^" -> new SsaInstruction.Constant(binOp.result(), 0);
                case "&", "|" -> new SsaInstruction.Assign(binOp.result(), binOp.left());
                default -> null;
            };
        }

        return null;
    }

    public void eliminateDeadPhis() {
        boolean phiChanged = true;
        while (phiChanged) {
            phiChanged = false;
            for (SsaBasicBlock block : ssaForm.blocks()) {
                List<SsaInstruction> toRemove = new ArrayList<>();
                for (SsaInstruction inst : block.instructions()) {
                    if (inst instanceof SsaInstruction.Phi phi) {
                        if (phi.result().uses().isEmpty()) {
                            toRemove.add(inst);
                            phiChanged = true;
                        } else {
                            Set<SsaVariable> uniqueIncoming = new HashSet<>(phi.incoming().values());
                            uniqueIncoming.remove(phi.result());
                            if (uniqueIncoming.size() == 1) {
                                SsaVariable replacement = uniqueIncoming.iterator().next();
                                ssaForm.replaceVariable(phi.result(), replacement);
                                toRemove.add(inst);
                                phiChanged = true;
                            }
                        }
                    }
                }
                for (SsaInstruction inst : toRemove) {
                    ssaForm.removeInstruction(inst, block);
                }
            }
        }
    }
}
