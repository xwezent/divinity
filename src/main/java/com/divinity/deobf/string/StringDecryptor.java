package com.divinity.deobf.string;

import com.divinity.classfile.ClassFileParser;
import com.divinity.classfile.ConstantPool;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.InstructionDecoder;
import com.divinity.bytecode.Opcode;
import java.util.*;

public final class StringDecryptor {

    private final ClassFileParser classParser;

    public StringDecryptor(ClassFileParser classParser) {
        this.classParser = classParser;
    }

    public record DecryptCall(String methodOwner, String methodName, String methodDesc,
                               Object[] args, String result) {}

    public record StringRef(int poolIndex, String value) {}

    public String tryDecrypt(List<Instruction> instructions,
                              Map<Integer, String> cpStrings,
                              ClassFileParser.MethodEntry decryptMethod) {

        if (decryptMethod.code() == null) return null;

        InstructionDecoder decoder = new InstructionDecoder(classParser);
        List<Instruction> methodInsns = decoder.decode(decryptMethod.code().bytecode());

        Map<Integer, Object> locals = new LinkedHashMap<>();

        String descriptor = decryptMethod.descriptor();
        if (descriptor != null && descriptor.length() > 2) {
            String params = descriptor.substring(1, descriptor.indexOf(')'));
            List<String> paramTypes = splitTypes(params);
            int slot = hasFlag(decryptMethod.accessFlags(), 0x0008) ? 0 : 1;
            for (String type : paramTypes) {
                locals.put(slot, defaultValue(type));
                slot += (type.equals("J") || type.equals("D")) ? 2 : 1;
            }
        }

        Deque<Object> stack = new ArrayDeque<>();
        Object returnValue = null;

        for (Instruction inst : methodInsns) {
            Opcode op = inst.opcode;
            int[] ops = inst.operands;

            if (op.isReturn()) {
                returnValue = stack.isEmpty() ? null : stack.pop();
                break;
            }
            if (op == Opcode.ATHROW) {
                return null;
            }

            executeInstruction(inst, stack, locals, cpStrings);
        }

        if (returnValue instanceof String s) return s;
        if (returnValue instanceof StringRef sr) return sr.value();
        if (returnValue instanceof Integer i) {
            int idx = i;
            ConstantPool.Entry e = classParser.cp(idx);
            if (e instanceof ConstantPool.Entry.Utf8 u) return u.value();
            if (e instanceof ConstantPool.Entry.Str s) {
                String ref = classParser.cpUtf8(s.stringIndex());
                if (ref != null) return ref;
            }
        }
        return returnValue != null ? returnValue.toString() : null;
    }

    public Object emulateMethod(List<Instruction> instructions,
                                 ClassFileParser.MethodEntry method,
                                 Object... args) {

        Map<Integer, Object> locals = new LinkedHashMap<>();
        String descriptor = method.descriptor();
        if (descriptor != null && descriptor.length() > 2) {
            String params = descriptor.substring(1, descriptor.indexOf(')'));
            List<String> paramTypes = splitTypes(params);
            int slot = hasFlag(method.accessFlags(), 0x0008) ? 0 : 1;
            for (int i = 0; i < paramTypes.size() && i < args.length; i++) {
                locals.put(slot, args[i]);
                slot += (paramTypes.get(i).equals("J") || paramTypes.get(i).equals("D")) ? 2 : 1;
            }
        }

        Deque<Object> stack = new ArrayDeque<>();
        Map<Integer, String> emptyCp = Map.of();

        for (Instruction inst : instructions) {
            Opcode op = inst.opcode;
            if (op.isReturn()) {
                return stack.isEmpty() ? null : stack.pop();
            }
            if (op == Opcode.ATHROW) {
                return null;
            }
            executeInstruction(inst, stack, locals, emptyCp);
        }

        return null;
    }

    private void executeInstruction(Instruction inst, Deque<Object> stack,
                                     Map<Integer, Object> locals, Map<Integer, String> cpStrings) {
        Opcode op = inst.opcode;
        int[] ops = inst.operands;

        switch (op) {
            case ACONST_NULL -> stack.push(null);
            case ICONST_M1 -> stack.push(-1);
            case ICONST_0 -> stack.push(0);
            case ICONST_1 -> stack.push(1);
            case ICONST_2 -> stack.push(2);
            case ICONST_3 -> stack.push(3);
            case ICONST_4 -> stack.push(4);
            case ICONST_5 -> stack.push(5);
            case BIPUSH -> stack.push((int)(byte)ops[0]);
            case SIPUSH -> stack.push((int)(short)((ops[0] << 8) | ops[1]));

            case LDC -> {
                int idx = ops[0] & 0xFF;
                ConstantPool.Entry e = classParser.cp(idx);
                stack.push(resolveCp(e, cpStrings));
            }
            case LDC_W -> {
                int idx = (ops[0] << 8) | ops[1];
                ConstantPool.Entry e = classParser.cp(idx);
                stack.push(resolveCp(e, cpStrings));
            }

            case ILOAD, ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3 -> {
                int idx = varIndex(op, ops);
                Object val = locals.get(idx);
                stack.push(val instanceof Integer i ? i : 0);
            }
            case ALOAD, ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3 -> {
                int idx = varIndex(op, ops);
                stack.push(locals.get(idx));
            }
            case ISTORE, ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3 -> {
                int idx = varIndex(op, ops);
                Object val = stack.isEmpty() ? 0 : stack.pop();
                locals.put(idx, val instanceof Integer i ? i : 0);
            }
            case ASTORE, ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3 -> {
                int idx = varIndex(op, ops);
                Object val = stack.isEmpty() ? null : stack.pop();
                locals.put(idx, val);
            }

            case IADD -> binIntOp((a, b) -> a + b, stack);
            case ISUB -> binIntOp((a, b) -> a - b, stack);
            case IMUL -> binIntOp((a, b) -> a * b, stack);
            case IDIV -> binIntOpSafe((a, b) -> b != 0 ? a / b : 0, stack);
            case IREM -> binIntOpSafe((a, b) -> b != 0 ? a % b : 0, stack);
            case IAND -> binIntOp((a, b) -> a & b, stack);
            case IOR  -> binIntOp((a, b) -> a | b, stack);
            case IXOR -> binIntOp((a, b) -> a ^ b, stack);
            case ISHL -> binIntOp((a, b) -> a << b, stack);
            case ISHR -> binIntOp((a, b) -> a >> b, stack);
            case IUSHR -> binIntOp((a, b) -> a >>> b, stack);
            case INEG -> {
                Object v = stack.isEmpty() ? 0 : stack.pop();
                stack.push(-(toInt(v)));
            }

            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                Object r = stack.isEmpty() ? 0 : stack.pop();
                Object l = stack.isEmpty() ? 0 : stack.pop();
                stack.push(0);
            }
            case GOTO -> {}
            case IRETURN, ARETURN -> {}

            case NEWARRAY -> {
                Object size = stack.isEmpty() ? 0 : stack.pop();
                stack.push(new int[toInt(size)]);
            }
            case IALOAD -> {
                Object idx = stack.isEmpty() ? 0 : stack.pop();
                Object arr = stack.isEmpty() ? null : stack.pop();
                if (arr instanceof int[] ia) {
                    int i = toInt(idx);
                    stack.push(i >= 0 && i < ia.length ? ia[i] : 0);
                } else {
                    stack.push(0);
                }
            }
            case IASTORE -> {
                Object val = stack.isEmpty() ? 0 : stack.pop();
                Object idx = stack.isEmpty() ? 0 : stack.pop();
                Object arr = stack.isEmpty() ? null : stack.pop();
                if (arr instanceof int[] ia) {
                    int i = toInt(idx);
                    if (i >= 0 && i < ia.length) ia[i] = toInt(val);
                }
            }
            case ARRAYLENGTH -> {
                Object arr = stack.isEmpty() ? null : stack.pop();
                if (arr instanceof int[] ia) stack.push(ia.length);
                else if (arr instanceof String s) stack.push(s.length());
                else stack.push(0);
            }

            case INVOKESTATIC -> {}
            case INVOKEVIRTUAL -> {
                int ridx = (ops[0] << 8) | ops[1];
                String name = classParser.cpMemberName(ridx);
                String desc = classParser.cpMemberDescriptor(ridx);
                if ("charAt".equals(name) && "(I)C".equals(desc)) {
                    Object idx = stack.isEmpty() ? 0 : stack.pop();
                    Object str = stack.isEmpty() ? null : stack.pop();
                    if (str instanceof String s) {
                        int i = toInt(idx);
                        stack.push(i >= 0 && i < s.length() ? (int) s.charAt(i) : 0);
                    } else {
                        stack.push(0);
                    }
                } else if ("toCharArray".equals(name)) {
                    Object str = stack.isEmpty() ? null : stack.pop();
                    if (str instanceof String s) {
                        int[] chars = new int[s.length()];
                        for (int i = 0; i < s.length(); i++) chars[i] = s.charAt(i);
                        stack.push(chars);
                    } else {
                        stack.push(new int[0]);
                    }
                } else if ("length".equals(name)) {
                    Object str = stack.isEmpty() ? null : stack.pop();
                    if (str instanceof String s) stack.push(s.length());
                    else stack.push(0);
                } else if ("hashCode".equals(name) && "()I".equals(desc)) {
                    Object str = stack.isEmpty() ? null : stack.pop();
                    if (str instanceof String s) stack.push(s.hashCode());
                    else stack.push(0);
                } else {
                    popStackIgnore(desc, stack);
                    stack.push(0);
                }
            }
            case INVOKESPECIAL -> {
                int ridx = (ops[0] << 8) | ops[1];
                String name = classParser.cpMemberName(ridx);
                String desc = classParser.cpMemberDescriptor(ridx);
                if ("<init>".equals(name)) {
                    String owner = classParser.cpMethodOwner(ridx);
                    if ("java/lang/String".equals(owner) && "([C)V".equals(desc)) {
                        Object chars = stack.isEmpty() ? null : stack.pop();
                        Object strObj = stack.isEmpty() ? null : stack.pop();
                        if (chars instanceof int[] ca) {
                            StringBuilder sb = new StringBuilder();
                            for (int c : ca) sb.append((char) c);
                            stack.push(sb.toString());
                        } else {
                            stack.push("");
                        }
                    } else if ("java/lang/StringBuilder".equals(owner)) {
                        Object sb = stack.isEmpty() ? null : stack.pop();
                        stack.push(new StringBuilder());
                    } else {
                        popStackIgnore(desc, stack);
                        stack.pop();
                    }
                } else {
                    popStackIgnore(desc, stack);
                    stack.pop();
                }
            }

            case DUP -> {
                if (!stack.isEmpty()) stack.push(stack.peek());
            }
            case POP -> {
                if (!stack.isEmpty()) stack.pop();
            }
            case I2C -> {
                Object v = stack.isEmpty() ? 0 : stack.pop();
                stack.push(toInt(v) & 0xFFFF);
            }

            default -> {}
        }
    }

    private Object resolveCp(ConstantPool.Entry e, Map<Integer, String> cpStrings) {
        if (e instanceof ConstantPool.Entry.Utf8 u) return u.value();
        if (e instanceof ConstantPool.Entry.Str s) return new StringRef(s.stringIndex(), classParser.cpUtf8(s.stringIndex()));
        if (e instanceof ConstantPool.Entry.Int i) return i.value();
        if (e instanceof ConstantPool.Entry.Float f) return f.value();
        if (e instanceof ConstantPool.Entry.Long l) return l.value();
        if (e instanceof ConstantPool.Entry.Double d) return d.value();
        return null;
    }

    private int varIndex(Opcode op, int[] ops) {
        return switch (op) {
            case ILOAD, ALOAD, ISTORE, ASTORE -> ops.length > 0 ? ops[0] & 0xFF : 0;
            case ILOAD_0, ALOAD_0, ISTORE_0, ASTORE_0 -> 0;
            case ILOAD_1, ALOAD_1, ISTORE_1, ASTORE_1 -> 1;
            case ILOAD_2, ALOAD_2, ISTORE_2, ASTORE_2 -> 2;
            case ILOAD_3, ALOAD_3, ISTORE_3, ASTORE_3 -> 3;
            default -> 0;
        };
    }

    private void binIntOp(java.util.function.BinaryOperator<Integer> op, Deque<Object> stack) {
        Object r = stack.isEmpty() ? 0 : stack.pop();
        Object l = stack.isEmpty() ? 0 : stack.pop();
        stack.push(op.apply(toInt(l), toInt(r)));
    }

    private void binIntOpSafe(java.util.function.BinaryOperator<Integer> op, Deque<Object> stack) {
        Object r = stack.isEmpty() ? 0 : stack.pop();
        Object l = stack.isEmpty() ? 0 : stack.pop();
        try {
            stack.push(op.apply(toInt(l), toInt(r)));
        } catch (Exception e) {
            stack.push(0);
        }
    }

    private int toInt(Object o) {
        if (o instanceof Integer i) return i;
        if (o instanceof Long l) return (int)(long)l;
        if (o instanceof Short s) return (int)(short)s;
        if (o instanceof Byte b) return (int)(byte)b;
        if (o instanceof Character c) return c;
        return 0;
    }

    private void popStackIgnore(String descriptor, Deque<Object> stack) {
        if (descriptor == null) return;
        int parenEnd = descriptor.indexOf(')');
        if (parenEnd <= 1) return;
        String sig = descriptor.substring(1, parenEnd);
        List<String> types = splitTypes(sig);
        for (int i = types.size() - 1; i >= 0; i--) {
            if (!stack.isEmpty()) stack.pop();
        }
    }

    private List<String> splitTypes(String sig) {
        List<String> types = new ArrayList<>();
        int i = 0;
        while (i < sig.length()) {
            int start = i;
            while (i < sig.length() && sig.charAt(i) == '[') i++;
            if (i >= sig.length()) break;
            if (sig.charAt(i) == 'L') {
                int end = sig.indexOf(';', i);
                if (end < 0) end = sig.length();
                types.add(sig.substring(start, end + 1));
                i = end + 1;
            } else {
                types.add(sig.substring(start, i + 1));
                i++;
            }
        }
        return types;
    }

    private Object defaultValue(String type) {
        return switch (type) {
            case "I", "Z", "B", "C", "S" -> 0;
            case "J" -> 0L;
            case "F" -> 0.0f;
            case "D" -> 0.0;
            default -> null;
        };
    }

    private boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
