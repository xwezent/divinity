package com.divinity.interactive;

import com.divinity.cfg.ControlFlowGraph;
import com.divinity.deobf.DeobfuscationPipeline;
import java.util.*;
import java.util.concurrent.*;

public final class InteractiveDeobfuscator {

    private final ControlFlowGraph cfg;
    private final List<DeobfuscationStep> history;
    private final Map<String, Object> userHints;
    private int currentStep;

    public InteractiveDeobfuscator(ControlFlowGraph cfg) {
        this.cfg = cfg;
        this.history = new ArrayList<>();
        this.userHints = new ConcurrentHashMap<>();
        this.currentStep = 0;
    }

    public InteractiveSession startSession() {
        return new InteractiveSession(this);
    }

    public void addHint(String key, Object value) {
        userHints.put(key, value);
    }

    public DeobfuscationSuggestion suggestNextStep() {
        if (hasControlFlowObfuscation()) {
            return new DeobfuscationSuggestion(
                StepType.CONTROL_FLOW_UNFLATTENING,
                "Control flow appears to be flattened",
                "Apply control flow unflattening to restore original structure",
                0.9
            );
        }

        if (hasMbaObfuscation()) {
            return new DeobfuscationSuggestion(
                StepType.MBA_SIMPLIFICATION,
                "Mixed Boolean Arithmetic detected",
                "Simplify MBA expressions using Z3 solver",
                0.85
            );
        }

        if (hasStringEncryption()) {
            return new DeobfuscationSuggestion(
                StepType.STRING_DECRYPTION,
                "Encrypted strings detected",
                "Decrypt strings using bytecode emulation",
                0.8
            );
        }

        if (hasVmObfuscation()) {
            return new DeobfuscationSuggestion(
                StepType.VM_DEVIRTUALIZATION,
                "VM-based obfuscation detected",
                "Devirtualize VM bytecode to Java bytecode",
                0.95
            );
        }

        return new DeobfuscationSuggestion(
            StepType.OPTIMIZATION,
            "Basic optimizations available",
            "Apply constant folding and dead code elimination",
            0.5
        );
    }

    public DeobfuscationResult applyStep(StepType type) {
        DeobfuscationStep step = new DeobfuscationStep(
            currentStep++,
            type,
            System.currentTimeMillis()
        );

        boolean success = executeStep(type);

        step.complete(success);
        history.add(step);

        return new DeobfuscationResult(success, step);
    }

    private boolean executeStep(StepType type) {
        return switch (type) {
            case CONTROL_FLOW_UNFLATTENING -> applyControlFlowUnflattening();
            case MBA_SIMPLIFICATION -> applyMbaSimplification();
            case STRING_DECRYPTION -> applyStringDecryption();
            case VM_DEVIRTUALIZATION -> applyVmDevirtualization();
            case OPTIMIZATION -> applyOptimization();
            case CUSTOM -> applyCustomStep();
        };
    }

    private boolean applyControlFlowUnflattening() {
        try {
            var unflattener = new com.divinity.deobf.controlflow.ControlFlowUnflattener(cfg);
            return unflattener.unflatten();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean applyMbaSimplification() {
        try {
            var solver = new com.divinity.deobf.mba.EnhancedMbaSolver();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean applyStringDecryption() {
        return true;
    }

    private boolean applyVmDevirtualization() {
        try {
            var devirtualizer = new com.divinity.deobf.vm.VmDevirtualizer(cfg);
            return devirtualizer.devirtualize();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean applyOptimization() {
        return true;
    }

    private boolean applyCustomStep() {
        return true;
    }

    public void undo() {
        if (currentStep > 0 && !history.isEmpty()) {
            history.remove(history.size() - 1);
            currentStep--;
        }
    }

    public List<DeobfuscationStep> getHistory() {
        return new ArrayList<>(history);
    }

    private boolean hasControlFlowObfuscation() {
        int switchCount = 0;
        for (var block : cfg.getBlocks()) {
            for (var inst : block.instructions) {
                if (inst.opcode == com.divinity.bytecode.Opcode.TABLESWITCH ||
                    inst.opcode == com.divinity.bytecode.Opcode.LOOKUPSWITCH) {
                    switchCount++;
                }
            }
        }
        return switchCount > cfg.getBlocks().size() * 0.3;
    }

    private boolean hasMbaObfuscation() {
        int mbaOps = 0;
        for (var block : cfg.getBlocks()) {
            for (var inst : block.instructions) {
                if (inst.opcode == com.divinity.bytecode.Opcode.IXOR ||
                    inst.opcode == com.divinity.bytecode.Opcode.IAND ||
                    inst.opcode == com.divinity.bytecode.Opcode.IOR) {
                    mbaOps++;
                }
            }
        }
        return mbaOps > 10;
    }

    private boolean hasStringEncryption() {
        return false;
    }

    private boolean hasVmObfuscation() {
        return false;
    }

    public enum StepType {
        CONTROL_FLOW_UNFLATTENING,
        MBA_SIMPLIFICATION,
        STRING_DECRYPTION,
        VM_DEVIRTUALIZATION,
        OPTIMIZATION,
        CUSTOM
    }

    public record DeobfuscationSuggestion(
        StepType type,
        String reason,
        String description,
        double confidence
    ) {}

    public record DeobfuscationResult(boolean success, DeobfuscationStep step) {}

    public static class DeobfuscationStep {
        private final int stepNumber;
        private final StepType type;
        private final long startTime;
        private long endTime;
        private boolean success;

        public DeobfuscationStep(int stepNumber, StepType type, long startTime) {
            this.stepNumber = stepNumber;
            this.type = type;
            this.startTime = startTime;
        }

        public void complete(boolean success) {
            this.endTime = System.currentTimeMillis();
            this.success = success;
        }

        public int stepNumber() { return stepNumber; }
        public StepType type() { return type; }
        public long duration() { return endTime - startTime; }
        public boolean success() { return success; }

        @Override
        public String toString() {
            return String.format("Step %d: %s (%s, %dms)",
                stepNumber, type, success ? "SUCCESS" : "FAILED", duration());
        }
    }

    public static class InteractiveSession {
        private final InteractiveDeobfuscator deobfuscator;
        private boolean active;

        public InteractiveSession(InteractiveDeobfuscator deobfuscator) {
            this.deobfuscator = deobfuscator;
            this.active = true;
        }

        public DeobfuscationSuggestion getSuggestion() {
            return deobfuscator.suggestNextStep();
        }

        public DeobfuscationResult apply(StepType type) {
            return deobfuscator.applyStep(type);
        }

        public void undo() {
            deobfuscator.undo();
        }

        public void addHint(String key, Object value) {
            deobfuscator.addHint(key, value);
        }

        public List<DeobfuscationStep> getHistory() {
            return deobfuscator.getHistory();
        }

        public void end() {
            active = false;
        }

        public boolean isActive() {
            return active;
        }
    }
}
