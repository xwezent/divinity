package com.divinity.types;

import com.divinity.cfg.BasicBlock;
import com.divinity.cfg.ControlFlowGraph;
import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import com.divinity.classfile.ClassFileParser;
import java.util.*;

public final class TypeInference {

    private final ControlFlowGraph cfg;
    private final ClassFileParser classParser;
    private final Map<String, TypeVariable> variables;
    private final List<TypeConstraint> constraints;
    private int typeVarCounter = 0;

    public TypeInference(ControlFlowGraph cfg, ClassFileParser classParser) {
        this.cfg = cfg;
        this.classParser = classParser;
        this.variables = new LinkedHashMap<>();
        this.constraints = new ArrayList<>();
    }

    public Map<String, JavaType> inferTypes() {
        collectConstraints();
        solveConstraints();
        return resolveTypes();
    }

    private void collectConstraints() {
        Deque<TypeVariable> stack = new ArrayDeque<>();

        for (BasicBlock block : cfg.getBlocks()) {
            for (Instruction inst : block.instructions) {
                processInstruction(inst, stack);
            }
        }
    }

    private void processInstruction(Instruction inst, Deque<TypeVariable> stack) {
        Opcode op = inst.opcode;

        switch (op) {
            case ACONST_NULL -> {
                TypeVariable var = newTypeVar("null");
                constraints.add(new TypeConstraint.Assignment(var, new JavaType.Null()));
                stack.push(var);
            }

            case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
                 BIPUSH, SIPUSH -> {
                TypeVariable var = newTypeVar("const");
                constraints.add(new TypeConstraint.Assignment(var, JavaType.Primitive.INT));
                stack.push(var);
            }

            case LCONST_0, LCONST_1 -> {
                TypeVariable var = newTypeVar("const");
                constraints.add(new TypeConstraint.Assignment(var, JavaType.Primitive.LONG));
                stack.push(var);
            }

            case FCONST_0, FCONST_1, FCONST_2 -> {
                TypeVariable var = newTypeVar("const");
                constraints.add(new TypeConstraint.Assignment(var, JavaType.Primitive.FLOAT));
                stack.push(var);
            }

            case DCONST_0, DCONST_1 -> {
                TypeVariable var = newTypeVar("const");
                constraints.add(new TypeConstraint.Assignment(var, JavaType.Primitive.DOUBLE));
                stack.push(var);
            }

            case LDC, LDC_W -> {
                int idx = op == Opcode.LDC ? (inst.operands[0] & 0xFF) :
                        ((inst.operands[0] << 8) | inst.operands[1]);
                TypeVariable var = newTypeVar("ldc");
                JavaType type = inferConstantType(idx);
                constraints.add(new TypeConstraint.Assignment(var, type));
                stack.push(var);
            }

            case ILOAD, ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3 -> {
                int varIdx = getVarIndex(inst);
                TypeVariable var = getOrCreateVar("var" + varIdx);
                constraints.add(new TypeConstraint.Assignment(var, JavaType.Primitive.INT));
                stack.push(var);
            }

            case LLOAD, LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3 -> {
                int varIdx = getVarIndex(inst);
                TypeVariable var = getOrCreateVar("var" + varIdx);
                constraints.add(new TypeConstraint.Assignment(var, JavaType.Primitive.LONG));
                stack.push(var);
            }

            case FLOAD, FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3 -> {
                int varIdx = getVarIndex(inst);
                TypeVariable var = getOrCreateVar("var" + varIdx);
                constraints.add(new TypeConstraint.Assignment(var, JavaType.Primitive.FLOAT));
                stack.push(var);
            }

            case DLOAD, DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3 -> {
                int varIdx = getVarIndex(inst);
                TypeVariable var = getOrCreateVar("var" + varIdx);
                constraints.add(new TypeConstraint.Assignment(var, JavaType.Primitive.DOUBLE));
                stack.push(var);
            }

            case ALOAD, ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3 -> {
                int varIdx = getVarIndex(inst);
                TypeVariable var = getOrCreateVar("var" + varIdx);
                stack.push(var);
            }

            case ISTORE, ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3 -> {
                int varIdx = getVarIndex(inst);
                TypeVariable var = getOrCreateVar("var" + varIdx);
                TypeVariable value = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                constraints.add(new TypeConstraint.Equality(var, value));
                constraints.add(new TypeConstraint.Assignment(var, JavaType.Primitive.INT));
            }

            case LSTORE, LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3 -> {
                int varIdx = getVarIndex(inst);
                TypeVariable var = getOrCreateVar("var" + varIdx);
                TypeVariable value = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                constraints.add(new TypeConstraint.Equality(var, value));
                constraints.add(new TypeConstraint.Assignment(var, JavaType.Primitive.LONG));
            }

            case FSTORE, FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3 -> {
                int varIdx = getVarIndex(inst);
                TypeVariable var = getOrCreateVar("var" + varIdx);
                TypeVariable value = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                constraints.add(new TypeConstraint.Equality(var, value));
                constraints.add(new TypeConstraint.Assignment(var, JavaType.Primitive.FLOAT));
            }

            case DSTORE, DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3 -> {
                int varIdx = getVarIndex(inst);
                TypeVariable var = getOrCreateVar("var" + varIdx);
                TypeVariable value = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                constraints.add(new TypeConstraint.Equality(var, value));
                constraints.add(new TypeConstraint.Assignment(var, JavaType.Primitive.DOUBLE));
            }

            case ASTORE, ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3 -> {
                int varIdx = getVarIndex(inst);
                TypeVariable var = getOrCreateVar("var" + varIdx);
                TypeVariable value = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                constraints.add(new TypeConstraint.Equality(var, value));
            }

            case IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR -> {
                TypeVariable right = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                TypeVariable left = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                TypeVariable result = newTypeVar("binop");
                constraints.add(new TypeConstraint.Assignment(result, JavaType.Primitive.INT));
                stack.push(result);
            }

            case INVOKEVIRTUAL, INVOKEINTERFACE, INVOKESPECIAL, INVOKESTATIC -> {
                int ridx = (inst.operands[0] << 8) | inst.operands[1];
                String descriptor = classParser.cpMemberDescriptor(ridx);
                String methodName = classParser.cpMemberName(ridx);
                String owner = classParser.cpMethodOwner(ridx);

                List<TypeVariable> args = popMethodArgs(stack, descriptor);
                TypeVariable receiver = null;
                if (op != Opcode.INVOKESTATIC) {
                    receiver = stack.isEmpty() ? newTypeVar("receiver") : stack.pop();
                    if (owner != null) {
                        JavaType ownerType = new JavaType.Class(owner.replace('/', '.'));
                        constraints.add(new TypeConstraint.Assignment(receiver, ownerType));
                    }
                }

                JavaType returnType = extractReturnType(descriptor);
                if (!(returnType instanceof JavaType.Primitive p && p.name().equals("void"))) {
                    TypeVariable result = newTypeVar("call");
                    constraints.add(new TypeConstraint.Assignment(result, returnType));
                    stack.push(result);

                    if (receiver != null) {
                        constraints.add(new TypeConstraint.MethodCall(
                            receiver, methodName, descriptor, args, result));
                    }
                }
            }

            case GETFIELD, GETSTATIC -> {
                int ridx = (inst.operands[0] << 8) | inst.operands[1];
                String descriptor = classParser.cpMemberDescriptor(ridx);
                String fieldName = classParser.cpMemberName(ridx);
                String owner = classParser.cpMethodOwner(ridx);

                TypeVariable receiver = null;
                if (op == Opcode.GETFIELD) {
                    receiver = stack.isEmpty() ? newTypeVar("receiver") : stack.pop();
                    if (owner != null) {
                        JavaType ownerType = new JavaType.Class(owner.replace('/', '.'));
                        constraints.add(new TypeConstraint.Assignment(receiver, ownerType));
                    }
                }

                TypeVariable result = newTypeVar("field");
                JavaType fieldType = JavaType.fromDescriptor(descriptor);
                constraints.add(new TypeConstraint.Assignment(result, fieldType));
                stack.push(result);

                if (receiver != null) {
                    constraints.add(new TypeConstraint.FieldAccess(
                        receiver, fieldName, descriptor, result));
                }
            }

            case PUTFIELD, PUTSTATIC -> {
                int ridx = (inst.operands[0] << 8) | inst.operands[1];
                String descriptor = classParser.cpMemberDescriptor(ridx);
                TypeVariable value = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                TypeVariable receiver = null;
                if (op == Opcode.PUTFIELD) {
                    receiver = stack.isEmpty() ? newTypeVar("receiver") : stack.pop();
                }
                JavaType fieldType = JavaType.fromDescriptor(descriptor);
                constraints.add(new TypeConstraint.Subtype(value, newTypeVarWithType("field", fieldType)));
            }

            case CHECKCAST -> {
                int ridx = (inst.operands[0] << 8) | inst.operands[1];
                String className = classParser.cpClassName(ridx);
                TypeVariable source = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                TypeVariable result = newTypeVar("cast");
                JavaType targetType = new JavaType.Class(className.replace('/', '.'));
                constraints.add(new TypeConstraint.Cast(source, targetType, result));
                constraints.add(new TypeConstraint.Assignment(result, targetType));
                stack.push(result);
            }

            case INSTANCEOF -> {
                int ridx = (inst.operands[0] << 8) | inst.operands[1];
                String className = classParser.cpClassName(ridx);
                TypeVariable source = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                JavaType targetType = new JavaType.Class(className.replace('/', '.'));
                constraints.add(new TypeConstraint.InstanceOf(source, targetType));
                TypeVariable result = newTypeVar("instanceof");
                constraints.add(new TypeConstraint.Assignment(result, JavaType.Primitive.BOOLEAN));
                stack.push(result);
            }

            case NEW -> {
                int ridx = (inst.operands[0] << 8) | inst.operands[1];
                String className = classParser.cpClassName(ridx);
                TypeVariable result = newTypeVar("new");
                JavaType type = new JavaType.Class(className.replace('/', '.'));
                constraints.add(new TypeConstraint.NewInstance(result, type));
                constraints.add(new TypeConstraint.Assignment(result, type));
                stack.push(result);
            }

            case NEWARRAY -> {
                TypeVariable size = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                TypeVariable result = newTypeVar("array");
                int atype = inst.operands[0];
                JavaType elementType = switch (atype) {
                    case 4 -> JavaType.Primitive.BOOLEAN;
                    case 5 -> JavaType.Primitive.CHAR;
                    case 6 -> JavaType.Primitive.FLOAT;
                    case 7 -> JavaType.Primitive.DOUBLE;
                    case 8 -> JavaType.Primitive.BYTE;
                    case 9 -> JavaType.Primitive.SHORT;
                    case 10 -> JavaType.Primitive.INT;
                    case 11 -> JavaType.Primitive.LONG;
                    default -> new JavaType.Unknown();
                };
                constraints.add(new TypeConstraint.Assignment(result, new JavaType.Array(elementType)));
                stack.push(result);
            }

            case ANEWARRAY -> {
                int ridx = (inst.operands[0] << 8) | inst.operands[1];
                String className = classParser.cpClassName(ridx);
                TypeVariable size = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                TypeVariable result = newTypeVar("array");
                JavaType elementType = new JavaType.Class(className.replace('/', '.'));
                constraints.add(new TypeConstraint.Assignment(result, new JavaType.Array(elementType)));
                stack.push(result);
            }

            case IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD -> {
                TypeVariable index = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                TypeVariable array = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                TypeVariable element = newTypeVar("element");
                constraints.add(new TypeConstraint.ArrayAccess(array, index, element));
                stack.push(element);
            }

            case IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE -> {
                TypeVariable value = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                TypeVariable index = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                TypeVariable array = stack.isEmpty() ? newTypeVar("tmp") : stack.pop();
                constraints.add(new TypeConstraint.ArrayStore(array, index, value));
            }

            case DUP -> {
                if (!stack.isEmpty()) {
                    TypeVariable top = stack.peek();
                    stack.push(top);
                }
            }

            case POP -> {
                if (!stack.isEmpty()) stack.pop();
            }

            default -> {}
        }
    }

    private TypeVariable newTypeVar(String baseName) {
        return new TypeVariable(baseName, typeVarCounter++);
    }

    private TypeVariable newTypeVarWithType(String baseName, JavaType type) {
        TypeVariable var = newTypeVar(baseName);
        var.setResolvedType(type);
        return var;
    }

    private TypeVariable getOrCreateVar(String name) {
        return variables.computeIfAbsent(name, k -> newTypeVar(name));
    }

    private int getVarIndex(Instruction inst) {
        return switch (inst.opcode) {
            case ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
                 ISTORE, LSTORE, FSTORE, DSTORE, ASTORE ->
                inst.operands.length > 0 ? inst.operands[0] & 0xFF : 0;
            case ILOAD_0, LLOAD_0, FLOAD_0, DLOAD_0, ALOAD_0,
                 ISTORE_0, LSTORE_0, FSTORE_0, DSTORE_0, ASTORE_0 -> 0;
            case ILOAD_1, LLOAD_1, FLOAD_1, DLOAD_1, ALOAD_1,
                 ISTORE_1, LSTORE_1, FSTORE_1, DSTORE_1, ASTORE_1 -> 1;
            case ILOAD_2, LLOAD_2, FLOAD_2, DLOAD_2, ALOAD_2,
                 ISTORE_2, LSTORE_2, FSTORE_2, DSTORE_2, ASTORE_2 -> 2;
            case ILOAD_3, LLOAD_3, FLOAD_3, DLOAD_3, ALOAD_3,
                 ISTORE_3, LSTORE_3, FSTORE_3, DSTORE_3, ASTORE_3 -> 3;
            default -> 0;
        };
    }

    private JavaType inferConstantType(int cpIndex) {
        var entry = classParser.cp(cpIndex);
        if (entry instanceof com.divinity.classfile.ConstantPool.Entry.Int) return JavaType.Primitive.INT;
        if (entry instanceof com.divinity.classfile.ConstantPool.Entry.Float) return JavaType.Primitive.FLOAT;
        if (entry instanceof com.divinity.classfile.ConstantPool.Entry.Long) return JavaType.Primitive.LONG;
        if (entry instanceof com.divinity.classfile.ConstantPool.Entry.Double) return JavaType.Primitive.DOUBLE;
        if (entry instanceof com.divinity.classfile.ConstantPool.Entry.Str) return new JavaType.Class("java.lang.String");
        if (entry instanceof com.divinity.classfile.ConstantPool.Entry.Utf8) return new JavaType.Class("java.lang.String");
        return new JavaType.Unknown();
    }

    private List<TypeVariable> popMethodArgs(Deque<TypeVariable> stack, String descriptor) {
        if (descriptor == null) return List.of();
        int parenEnd = descriptor.indexOf(')');
        if (parenEnd <= 1) return List.of();

        String params = descriptor.substring(1, parenEnd);
        int argCount = countArgs(params);

        List<TypeVariable> args = new ArrayList<>();
        for (int i = 0; i < argCount; i++) {
            args.add(stack.isEmpty() ? newTypeVar("arg") : stack.pop());
        }
        Collections.reverse(args);
        return args;
    }

    private int countArgs(String params) {
        int count = 0;
        int i = 0;
        while (i < params.length()) {
            while (i < params.length() && params.charAt(i) == '[') i++;
            if (i >= params.length()) break;
            if (params.charAt(i) == 'L') {
                i = params.indexOf(';', i) + 1;
            } else {
                i++;
            }
            count++;
        }
        return count;
    }

    private JavaType extractReturnType(String descriptor) {
        if (descriptor == null) return new JavaType.Unknown();
        int parenIdx = descriptor.indexOf(')');
        if (parenIdx < 0 || parenIdx + 1 >= descriptor.length()) return JavaType.Primitive.VOID;
        return JavaType.fromDescriptor(descriptor.substring(parenIdx + 1));
    }

    private void solveConstraints() {
        TypeUnification unification = new TypeUnification(constraints, variables);
        unification.solve();
    }

    private Map<String, JavaType> resolveTypes() {
        Map<String, JavaType> result = new LinkedHashMap<>();
        for (Map.Entry<String, TypeVariable> entry : variables.entrySet()) {
            result.put(entry.getKey(), entry.getValue().resolvedType());
        }
        return result;
    }
}
