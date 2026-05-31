package com.divinity.deobf.reflection;

import com.divinity.cfg.ControlFlowGraph;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import com.divinity.cfg.BasicBlock;
import com.divinity.classfile.ClassFileParser;
import java.util.*;

public final class ReflectionResolver {

    private final ControlFlowGraph cfg;
    private final ClassFileParser classParser;
    private final Map<Integer, ReflectionCall> resolvedCalls;

    public ReflectionResolver(ControlFlowGraph cfg, ClassFileParser classParser) {
        this.cfg = cfg;
        this.classParser = classParser;
        this.resolvedCalls = new LinkedHashMap<>();
    }

    public void resolve() {
        for (BasicBlock block : cfg.getBlocks()) {
            analyzeBlock(block);
        }
    }

    private void analyzeBlock(BasicBlock block) {
        List<Instruction> instructions = block.instructions;

        for (int i = 0; i < instructions.size(); i++) {
            Instruction inst = instructions.get(i);

            if (isReflectionCall(inst)) {
                ReflectionCall call = analyzeReflectionCall(instructions, i);
                if (call != null) {
                    resolvedCalls.put(i, call);
                }
            }
        }
    }

    private boolean isReflectionCall(Instruction inst) {
        if (inst.opcode != Opcode.INVOKEVIRTUAL && inst.opcode != Opcode.INVOKESTATIC) {
            return false;
        }

        int ridx = (inst.operands[0] << 8) | inst.operands[1];
        String owner = classParser.cpMethodOwner(ridx);
        String name = classParser.cpMemberName(ridx);

        if (owner == null || name == null) return false;

        return (owner.equals("java/lang/Class") &&
                (name.equals("forName") || name.equals("getMethod") ||
                 name.equals("getDeclaredMethod") || name.equals("getField"))) ||
               (owner.equals("java/lang/reflect/Method") && name.equals("invoke")) ||
               (owner.equals("java/lang/reflect/Field") &&
                (name.equals("get") || name.equals("set"))) ||
               (owner.equals("java/lang/reflect/Constructor") && name.equals("newInstance"));
    }

    private ReflectionCall analyzeReflectionCall(List<Instruction> instructions, int callIndex) {
        Instruction callInst = instructions.get(callIndex);
        int ridx = (callInst.operands[0] << 8) | callInst.operands[1];
        String owner = classParser.cpMethodOwner(ridx);
        String methodName = classParser.cpMemberName(ridx);

        if ("java/lang/Class".equals(owner) && "forName".equals(methodName)) {
            String className = findConstantString(instructions, callIndex);
            if (className != null) {
                return new ReflectionCall(ReflectionType.CLASS_FOR_NAME, className, null, null);
            }
        }

        if ("java/lang/Class".equals(owner) &&
            (methodName.equals("getMethod") || methodName.equals("getDeclaredMethod"))) {
            String targetMethodName = findConstantString(instructions, callIndex);
            if (targetMethodName != null) {
                return new ReflectionCall(ReflectionType.GET_METHOD, null, targetMethodName, null);
            }
        }

        if ("java/lang/Class".equals(owner) && methodName.equals("getField")) {
            String fieldName = findConstantString(instructions, callIndex);
            if (fieldName != null) {
                return new ReflectionCall(ReflectionType.GET_FIELD, null, null, fieldName);
            }
        }

        if ("java/lang/reflect/Method".equals(owner) && methodName.equals("invoke")) {
            return new ReflectionCall(ReflectionType.METHOD_INVOKE, null, null, null);
        }

        return null;
    }

    private String findConstantString(List<Instruction> instructions, int startIndex) {
        for (int i = startIndex - 1; i >= Math.max(0, startIndex - 10); i--) {
            Instruction inst = instructions.get(i);

            if (inst.opcode == Opcode.LDC || inst.opcode == Opcode.LDC_W) {
                int idx = inst.opcode == Opcode.LDC ?
                    (inst.operands[0] & 0xFF) :
                    ((inst.operands[0] << 8) | inst.operands[1]);

                var entry = classParser.cp(idx);
                if (entry instanceof com.divinity.classfile.ConstantPool.Entry.Str s) {
                    return classParser.cpUtf8(s.stringIndex());
                }
                if (entry instanceof com.divinity.classfile.ConstantPool.Entry.Utf8 u) {
                    return u.value();
                }
            }
        }
        return null;
    }

    public Map<Integer, ReflectionCall> getResolvedCalls() {
        return resolvedCalls;
    }

    public enum ReflectionType {
        CLASS_FOR_NAME,
        GET_METHOD,
        GET_FIELD,
        METHOD_INVOKE,
        FIELD_GET,
        FIELD_SET,
        NEW_INSTANCE
    }

    public record ReflectionCall(ReflectionType type, String className,
                                 String methodName, String fieldName) {
        @Override
        public String toString() {
            return switch (type) {
                case CLASS_FOR_NAME -> "Class.forName(\"" + className + "\")";
                case GET_METHOD -> "getMethod(\"" + methodName + "\")";
                case GET_FIELD -> "getField(\"" + fieldName + "\")";
                case METHOD_INVOKE -> "method.invoke()";
                case FIELD_GET -> "field.get()";
                case FIELD_SET -> "field.set()";
                case NEW_INSTANCE -> "constructor.newInstance()";
            };
        }

        public String toDirectCall() {
            return switch (type) {
                case CLASS_FOR_NAME -> className + ".class";
                case GET_METHOD -> methodName + "()";
                case GET_FIELD -> fieldName;
                default -> toString();
            };
        }
    }
}
