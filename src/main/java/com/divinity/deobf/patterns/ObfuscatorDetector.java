package com.divinity.deobf.patterns;

import com.divinity.cfg.ControlFlowGraph;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import com.divinity.cfg.BasicBlock;
import java.util.*;

public final class ObfuscatorDetector {

    private final ControlFlowGraph cfg;
    private final byte[] bytecode;

    public ObfuscatorDetector(ControlFlowGraph cfg, byte[] bytecode) {
        this.cfg = cfg;
        this.bytecode = bytecode;
    }

    public ObfuscatorInfo detect() {
        ObfuscatorInfo proguard = detectProguard();
        if (proguard != null) return proguard;

        ObfuscatorInfo zelix = detectZelix();
        if (zelix != null) return zelix;

        ObfuscatorInfo allatori = detectAllatori();
        if (allatori != null) return allatori;

        ObfuscatorInfo dashO = detectDashO();
        if (dashO != null) return dashO;

        ObfuscatorInfo yguard = detectYGuard();
        if (yguard != null) return yguard;

        return new ObfuscatorInfo(ObfuscatorType.NONE, "None", List.of());
    }

    private ObfuscatorInfo detectProguard() {
        List<String> signatures = new ArrayList<>();
        int score = 0;

        if (hasShortVariableNames()) {
            signatures.add("Short variable names (a, b, c)");
            score += 20;
        }

        if (hasLineNumberStripping()) {
            signatures.add("Line numbers stripped");
            score += 15;
        }

        if (hasSimpleControlFlow()) {
            signatures.add("Simple control flow (no complex obfuscation)");
            score += 10;
        }

        if (hasPackageFlattening()) {
            signatures.add("Package flattening");
            score += 15;
        }

        if (score >= 40) {
            return new ObfuscatorInfo(ObfuscatorType.PROGUARD, "ProGuard", signatures);
        }

        return null;
    }

    private ObfuscatorInfo detectZelix() {
        List<String> signatures = new ArrayList<>();
        int score = 0;

        if (hasZelixStringEncryption()) {
            signatures.add("Zelix string encryption pattern");
            score += 30;
        }

        if (hasZelixFlowObfuscation()) {
            signatures.add("Zelix control flow obfuscation");
            score += 25;
        }

        if (hasZelixVmPattern()) {
            signatures.add("Zelix VM bytecode");
            score += 35;
        }

        if (hasReflectionObfuscation()) {
            signatures.add("Heavy reflection usage");
            score += 15;
        }

        if (score >= 50) {
            return new ObfuscatorInfo(ObfuscatorType.ZELIX, "Zelix KlassMaster", signatures);
        }

        return null;
    }

    private ObfuscatorInfo detectAllatori() {
        List<String> signatures = new ArrayList<>();
        int score = 0;

        if (hasAllatoriStringEncryption()) {
            signatures.add("Allatori string encryption");
            score += 30;
        }

        if (hasAllatoriFlowObfuscation()) {
            signatures.add("Allatori flow obfuscation (switch-based)");
            score += 25;
        }

        if (hasAllatoriNameObfuscation()) {
            signatures.add("Allatori name obfuscation pattern");
            score += 20;
        }

        if (hasWatermarkPattern()) {
            signatures.add("Watermark pattern detected");
            score += 15;
        }

        if (score >= 50) {
            return new ObfuscatorInfo(ObfuscatorType.ALLATORI, "Allatori", signatures);
        }

        return null;
    }

    private ObfuscatorInfo detectDashO() {
        List<String> signatures = new ArrayList<>();
        int score = 0;

        if (hasDashOStringEncryption()) {
            signatures.add("DashO string encryption");
            score += 30;
        }

        if (hasDashOControlFlow()) {
            signatures.add("DashO control flow obfuscation");
            score += 25;
        }

        if (hasDashOVmPattern()) {
            signatures.add("DashO VM pattern");
            score += 30;
        }

        if (score >= 50) {
            return new ObfuscatorInfo(ObfuscatorType.DASHO, "DashO", signatures);
        }

        return null;
    }

    private ObfuscatorInfo detectYGuard() {
        List<String> signatures = new ArrayList<>();
        int score = 0;

        if (hasYGuardNamePattern()) {
            signatures.add("yGuard naming pattern");
            score += 25;
        }

        if (hasSimpleObfuscation()) {
            signatures.add("Simple obfuscation (name mangling only)");
            score += 20;
        }

        if (score >= 40) {
            return new ObfuscatorInfo(ObfuscatorType.YGUARD, "yGuard", signatures);
        }

        return null;
    }

    private boolean hasShortVariableNames() {
        int shortNames = 0;
        int totalVars = 0;

        for (BasicBlock block : cfg.getBlocks()) {
            for (Instruction inst : block.instructions) {
                if (isLoadOrStore(inst.opcode)) {
                    totalVars++;
                }
            }
        }

        return totalVars > 10 && shortNames > totalVars * 0.7;
    }

    private boolean hasLineNumberStripping() {
        return true;
    }

    private boolean hasSimpleControlFlow() {
        int complexBlocks = 0;
        for (BasicBlock block : cfg.getBlocks()) {
            if (block.successors.size() > 2) {
                complexBlocks++;
            }
        }
        return complexBlocks < cfg.getBlocks().size() * 0.1;
    }

    private boolean hasPackageFlattening() {
        return false;
    }

    private boolean hasZelixStringEncryption() {
        for (BasicBlock block : cfg.getBlocks()) {
            int xorCount = 0;
            int arrayLoadCount = 0;

            for (Instruction inst : block.instructions) {
                if (inst.opcode == Opcode.IXOR) xorCount++;
                if (inst.opcode == Opcode.BALOAD || inst.opcode == Opcode.CALOAD) arrayLoadCount++;
            }

            if (xorCount >= 3 && arrayLoadCount >= 2) {
                return true;
            }
        }
        return false;
    }

    private boolean hasZelixFlowObfuscation() {
        int switchBlocks = 0;
        for (BasicBlock block : cfg.getBlocks()) {
            for (Instruction inst : block.instructions) {
                if (inst.opcode == Opcode.TABLESWITCH || inst.opcode == Opcode.LOOKUPSWITCH) {
                    switchBlocks++;
                }
            }
        }
        return switchBlocks > cfg.getBlocks().size() * 0.3;
    }

    private boolean hasZelixVmPattern() {
        for (BasicBlock block : cfg.getBlocks()) {
            if (block.instructions.size() > 50) {
                int switchCount = 0;
                int arrayOps = 0;

                for (Instruction inst : block.instructions) {
                    if (inst.opcode == Opcode.TABLESWITCH) switchCount++;
                    if (inst.opcode == Opcode.BALOAD || inst.opcode == Opcode.BASTORE) arrayOps++;
                }

                if (switchCount >= 1 && arrayOps >= 5) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasReflectionObfuscation() {
        int reflectionCalls = 0;
        for (BasicBlock block : cfg.getBlocks()) {
            for (Instruction inst : block.instructions) {
                if (inst.opcode == Opcode.INVOKEVIRTUAL || inst.opcode == Opcode.INVOKESTATIC) {
                    reflectionCalls++;
                }
            }
        }
        return reflectionCalls > cfg.getBlocks().size() * 2;
    }

    private boolean hasAllatoriStringEncryption() {
        for (BasicBlock block : cfg.getBlocks()) {
            boolean hasInvokeDynamic = false;
            boolean hasStringConcat = false;

            for (Instruction inst : block.instructions) {
                if (inst.opcode == Opcode.INVOKEDYNAMIC) hasInvokeDynamic = true;
            }

            if (hasInvokeDynamic && hasStringConcat) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAllatoriFlowObfuscation() {
        return hasZelixFlowObfuscation();
    }

    private boolean hasAllatoriNameObfuscation() {
        return false;
    }

    private boolean hasWatermarkPattern() {
        return false;
    }

    private boolean hasDashOStringEncryption() {
        return hasZelixStringEncryption();
    }

    private boolean hasDashOControlFlow() {
        return hasZelixFlowObfuscation();
    }

    private boolean hasDashOVmPattern() {
        return hasZelixVmPattern();
    }

    private boolean hasYGuardNamePattern() {
        return false;
    }

    private boolean hasSimpleObfuscation() {
        return hasSimpleControlFlow() && !hasZelixFlowObfuscation();
    }

    private boolean isLoadOrStore(Opcode op) {
        return op == Opcode.ILOAD || op == Opcode.ISTORE ||
               op == Opcode.ALOAD || op == Opcode.ASTORE ||
               op == Opcode.LLOAD || op == Opcode.LSTORE ||
               op == Opcode.FLOAD || op == Opcode.FSTORE ||
               op == Opcode.DLOAD || op == Opcode.DSTORE;
    }

    public enum ObfuscatorType {
        NONE,
        PROGUARD,
        ZELIX,
        ALLATORI,
        DASHO,
        YGUARD,
        CUSTOM
    }

    public record ObfuscatorInfo(ObfuscatorType type, String name, List<String> signatures) {
        @Override
        public String toString() {
            return name + " (" + String.join(", ", signatures) + ")";
        }
    }
}
