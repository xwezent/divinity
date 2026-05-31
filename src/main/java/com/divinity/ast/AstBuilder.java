package com.divinity.ast;

import com.divinity.bytecode.Instruction;
import com.divinity.bytecode.Opcode;
import com.divinity.cfg.BasicBlock;
import com.divinity.cfg.ControlFlowGraph;
import com.divinity.classfile.ClassFileParser;
import com.divinity.classfile.ConstantPool;
import java.util.*;

public final class AstBuilder {

    private final ClassFileParser classParser;

    public AstBuilder(ClassFileParser classParser) {
        this.classParser = classParser;
    }

    public AstNode.ClassNode build() {
        ClassFileParser.ClassInfo info = classParser.getClassInfo();

        List<AstNode.FieldNode> fields = new ArrayList<>();
        if (info.fields != null) {
            for (var f : info.fields) {
                fields.add(new AstNode.FieldNode(
                        f.accessFlags(), f.name(), f.descriptor(), f.signature(), f.constValue()));
            }
        }

        List<AstNode.MethodNode> methods = new ArrayList<>();
        if (info.methods != null) {
            for (var m : info.methods) {
                methods.add(buildMethod(m));
            }
        }

        List<String> ifaces = info.interfaces != null ? new ArrayList<>(info.interfaces) : List.of();
        List<String> permitted = info.permittedSubclasses != null
                ? new ArrayList<>(info.permittedSubclasses) : List.of();

        List<AstNode.RecordComponentNode> recordComps = null;
        if (info.recordComponents != null) {
            recordComps = new ArrayList<>();
            for (var rc : info.recordComponents) {
                recordComps.add(new AstNode.RecordComponentNode(rc.name(), rc.descriptor(), rc.signature()));
            }
        }

        return new AstNode.ClassNode(
                info.accessFlags,
                info.thisClass != null ? info.thisClass.replace('/', '.') : "Unknown",
                info.superClass != null ? info.superClass.replace('/', '.') : "java.lang.Object",
                ifaces,
                fields,
                methods,
                List.of(),
                info.isInterface(),
                info.isEnum(),
                info.isRecord(),
                info.isAnnotation(),
                permitted,
                recordComps,
                info.signature,
                info.sourceFile
        );
    }

    private int cpRef(int[] operands) {
        return (operands[0] << 8) | operands[1];
    }

    private AstNode.MethodNode buildMethod(ClassFileParser.MethodEntry method) {
        List<AstNode.Statement> body = List.of();
        if (method.code() != null) {
            body = buildMethodBody(method);
        }

        List<String> exceptions = method.exceptions() != null
                ? Arrays.asList(method.exceptions()) : List.of();

        return new AstNode.MethodNode(
                method.accessFlags(),
                method.name(),
                method.descriptor(),
                method.signature(),
                exceptions,
                body
        );
    }

    private List<AstNode.Statement> buildMethodBody(ClassFileParser.MethodEntry method) {
        ClassFileParser.CodeAttribute code = method.code();
        if (code == null || code.bytecode().length == 0) {
            return List.of();
        }

        var decoder = new com.divinity.bytecode.InstructionDecoder(classParser);
        List<Instruction> instructions = decoder.decode(code.bytecode());

        if (instructions.isEmpty()) {
            return List.of();
        }

        String[] exTypes = null;
        if (code.exceptionTable() != null) {
            exTypes = new String[code.exceptionTable().length];
            for (int i = 0; i < code.exceptionTable().length; i++) {
                exTypes[i] = code.exceptionTable()[i].catchType();
            }
        }
        int[][] exTable = null;
        if (code.exceptionTable() != null) {
            exTable = new int[code.exceptionTable().length][4];
            for (int i = 0; i < code.exceptionTable().length; i++) {
                var h = code.exceptionTable()[i];
                exTable[i] = new int[]{h.startPc(), h.endPc(), h.handlerPc(), i};
            }
        }

        ControlFlowGraph cfg = new ControlFlowGraph(instructions, exTable, exTypes);
        cfg.computeDominators();

        var pipeline = new com.divinity.deobf.DeobfuscationPipeline(cfg);
        pipeline.run();

        cfg.collapseGotoChains();

        return buildStatementsFromInsns(instructions, method);
    }

    private List<AstNode.Statement> buildStatementsFromInsns(List<Instruction> instructions,
                                                              ClassFileParser.MethodEntry method) {
        List<AstNode.Statement> statements = new ArrayList<>();
        Deque<AstNode.Expression> stack = new ArrayDeque<>();

        for (Instruction inst : instructions) {
            processInstruction(inst, stack, statements, method);
        }

        return statements;
    }

    private void processInstruction(Instruction inst, Deque<AstNode.Expression> stack,
                                     List<AstNode.Statement> statements,
                                     ClassFileParser.MethodEntry method) {

        switch (inst.opcode) {
            case ACONST_NULL -> stack.push(AstNode.nullLiteral());
            case ICONST_M1 -> stack.push(AstNode.intLiteral(-1));
            case ICONST_0 -> stack.push(AstNode.intLiteral(0));
            case ICONST_1 -> stack.push(AstNode.intLiteral(1));
            case ICONST_2 -> stack.push(AstNode.intLiteral(2));
            case ICONST_3 -> stack.push(AstNode.intLiteral(3));
            case ICONST_4 -> stack.push(AstNode.intLiteral(4));
            case ICONST_5 -> stack.push(AstNode.intLiteral(5));
            case LCONST_0 -> stack.push(AstNode.longLiteral(0L));
            case LCONST_1 -> stack.push(AstNode.longLiteral(1L));
            case FCONST_0 -> stack.push(AstNode.floatLiteral(0.0f));
            case FCONST_1 -> stack.push(AstNode.floatLiteral(1.0f));
            case FCONST_2 -> stack.push(AstNode.floatLiteral(2.0f));
            case DCONST_0 -> stack.push(AstNode.doubleLiteral(0.0));
            case DCONST_1 -> stack.push(AstNode.doubleLiteral(1.0));
            case BIPUSH -> stack.push(AstNode.intLiteral((byte) inst.operands[0]));
            case SIPUSH -> stack.push(AstNode.intLiteral((short) ((inst.operands[0] << 8) | inst.operands[1])));
            case LDC, LDC_W -> {
                int idx = inst.opcode == Opcode.LDC
                        ? (inst.operands[0] & 0xFF)
                        : ((inst.operands[0] << 8) | inst.operands[1]);
                ConstantPool.Entry e = classParser.cp(idx);
                if (e instanceof ConstantPool.Entry.Utf8 u) stack.push(AstNode.stringLiteral(u.value()));
                else if (e instanceof ConstantPool.Entry.Str s) stack.push(AstNode.stringLiteral(classParser.cpUtf8(s.stringIndex())));
                else if (e instanceof ConstantPool.Entry.Int i) stack.push(AstNode.intLiteral(i.value()));
                else if (e instanceof ConstantPool.Entry.Float f) stack.push(AstNode.floatLiteral(f.value()));
                else if (e instanceof ConstantPool.Entry.Long l) stack.push(AstNode.longLiteral(l.value()));
                else if (e instanceof ConstantPool.Entry.Double d) stack.push(AstNode.doubleLiteral(d.value()));
                else stack.push(AstNode.nullLiteral());
            }

            case ILOAD, ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3,
                 LLOAD, LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3,
                 FLOAD, FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3,
                 DLOAD, DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3 ->
                stack.push(createVarExpr(inst, method));
            case ALOAD, ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3 ->
                stack.push(createVarExpr(inst, method));

            case ISTORE, ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3,
                 LSTORE, LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3,
                 FSTORE, FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3,
                 DSTORE, DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3,
                 ASTORE, ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3 -> {
                AstNode.Expression val = stack.isEmpty() ? AstNode.nullLiteral() : stack.pop();
                String varName = getVarName(inst, method);
                String desc = getLocalDescriptor(inst, method);
                if (!varName.equals("this")) {
                    statements.add(new AstNode.Statement.VarDeclStmt(
                            inferTypeFromDescriptor(desc), varName, val));
                }
            }

            case GETSTATIC -> {
                int ridx = cpRef(inst.operands);
                stack.push(new AstNode.Expression.StaticFieldExpr(
                        owner(ridx), memberName(ridx), memberDesc(ridx)));
            }
            case GETFIELD -> {
                int ridx = cpRef(inst.operands);
                AstNode.Expression obj = stack.isEmpty() ? new AstNode.Expression.ThisExpr() : stack.pop();
                stack.push(new AstNode.Expression.FieldAccessExpr(
                        obj, owner(ridx), memberName(ridx), memberDesc(ridx)));
            }

            case INVOKEVIRTUAL, INVOKEINTERFACE -> {
                int ridx = cpRef(inst.operands);
                String desc = classParser.cpMemberDescriptor(ridx);
                List<AstNode.Expression> args = popStackArgs(stack, desc);
                AstNode.Expression obj = stack.isEmpty() ? new AstNode.Expression.ThisExpr() : stack.pop();
                AstNode.Expression call = new AstNode.Expression.MethodCallExpr(
                        obj, owner(ridx), memberName(ridx), desc, args);
                String retType = extractReturnType(desc);
                if (!"void".equals(retType)) {
                    stack.push(call);
                } else {
                    statements.add(new AstNode.Statement.ExpressionStmt(call));
                }
            }
            case INVOKESTATIC -> {
                int ridx = cpRef(inst.operands);
                String desc = classParser.cpMemberDescriptor(ridx);
                List<AstNode.Expression> args = popStackArgs(stack, desc);
                AstNode.Expression call = new AstNode.Expression.StaticCallExpr(
                        owner(ridx), memberName(ridx), memberDesc(ridx), args);
                String retType = extractReturnType(desc);
                if (!"void".equals(retType)) {
                    stack.push(call);
                } else {
                    statements.add(new AstNode.Statement.ExpressionStmt(call));
                }
            }

            case PUTFIELD -> {
                int ridx = cpRef(inst.operands);
                AstNode.Expression val = stack.isEmpty() ? AstNode.nullLiteral() : stack.pop();
                AstNode.Expression obj = stack.isEmpty() ? new AstNode.Expression.ThisExpr() : stack.pop();
                statements.add(new AstNode.Statement.ExpressionStmt(
                        new AstNode.Expression.AssignExpr(
                                new AstNode.Expression.FieldAccessExpr(
                                        obj, owner(ridx), memberName(ridx), memberDesc(ridx)),
                                val)));
            }
            case PUTSTATIC -> {
                int ridx = cpRef(inst.operands);
                AstNode.Expression val = stack.isEmpty() ? AstNode.nullLiteral() : stack.pop();
                statements.add(new AstNode.Statement.ExpressionStmt(
                        new AstNode.Expression.AssignExpr(
                                new AstNode.Expression.StaticFieldExpr(
                                        owner(ridx), memberName(ridx), memberDesc(ridx)),
                                val)));
            }

            case IADD -> binOp("+", stack);
            case ISUB -> binOp("-", stack);
            case IMUL -> binOp("*", stack);
            case IDIV -> binOp("/", stack);
            case IREM -> binOp("%", stack);
            case IAND -> binOp("&", stack);
            case IOR  -> binOp("|", stack);
            case IXOR -> binOp("^", stack);
            case ISHL -> binOp("<<", stack);
            case ISHR -> binOp(">>", stack);
            case IUSHR -> binOp(">>>", stack);

            case NEW -> {
                int ridx = cpRef(inst.operands);
                stack.push(new AstNode.Expression.NewExpr(
                        classParser.cpClassName(ridx).replace('/', '.'), List.of(), false, 0));
            }
            case DUP -> {
                if (!stack.isEmpty()) stack.push(stack.peek());
            }
            case INVOKESPECIAL -> {
                int ridx = cpRef(inst.operands);
                String desc = classParser.cpMemberDescriptor(ridx);
                List<AstNode.Expression> args = popStackArgs(stack, desc);
                AstNode.Expression obj = stack.isEmpty() ? new AstNode.Expression.ThisExpr() : stack.pop();
                String mName = classParser.cpMemberName(ridx);
                if ("<init>".equals(mName)) {
                    AstNode.Expression call;
                    if (obj instanceof AstNode.Expression.NewExpr newExpr) {
                        call = new AstNode.Expression.MethodCallExpr(obj, owner(ridx), mName, desc, args);
                        stack.push(newExpr);
                    } else {
                        call = new AstNode.Expression.MethodCallExpr(obj, owner(ridx), mName, desc, args);
                    }
                    statements.add(new AstNode.Statement.ExpressionStmt(call));
                } else {
                    AstNode.Expression call = new AstNode.Expression.MethodCallExpr(
                            obj, owner(ridx), mName, desc, args);
                    String retType = extractReturnType(desc);
                    if (!"void".equals(retType)) {
                        stack.push(call);
                    } else {
                        statements.add(new AstNode.Statement.ExpressionStmt(call));
                    }
                }
            }

            case ARRAYLENGTH -> {
                AstNode.Expression arr = stack.isEmpty() ? AstNode.nullLiteral() : stack.pop();
                stack.push(new AstNode.Expression.FieldAccessExpr(arr, null, "length", "I"));
            }
            case CHECKCAST -> {
                int ridx = cpRef(inst.operands);
                AstNode.Expression top = stack.isEmpty() ? AstNode.nullLiteral() : stack.pop();
                stack.push(new AstNode.Expression.CastExpr(
                        classParser.cpClassName(ridx).replace('/', '.'), top));
            }
            case INSTANCEOF -> {
                int ridx = cpRef(inst.operands);
                AstNode.Expression top = stack.isEmpty() ? AstNode.nullLiteral() : stack.pop();
                stack.push(new AstNode.Expression.InstanceOfExpr(
                        top, classParser.cpClassName(ridx).replace('/', '.'), null));
            }

            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE -> {
                AstNode.Expression right = stack.isEmpty() ? AstNode.intLiteral(0) : stack.pop();
                AstNode.Expression left = stack.isEmpty() ? AstNode.intLiteral(0) : stack.pop();
                String op = switch (inst.opcode) {
                    case IF_ICMPEQ, IF_ACMPEQ -> "==";
                    case IF_ICMPNE, IF_ACMPNE -> "!=";
                    case IF_ICMPLT -> "<";
                    case IF_ICMPGE -> ">=";
                    case IF_ICMPGT -> ">";
                    case IF_ICMPLE -> "<=";
                    default -> "==";
                };
                stack.push(new AstNode.Expression.BinaryExpr(op, left, right));
            }
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> {
                AstNode.Expression val = stack.isEmpty() ? AstNode.intLiteral(0) : stack.pop();
                String op = switch (inst.opcode) {
                    case IFEQ -> "==";
                    case IFNE -> "!=";
                    case IFLT -> "<";
                    case IFGE -> ">=";
                    case IFGT -> ">";
                    case IFLE -> "<=";
                    default -> "==";
                };
                stack.push(new AstNode.Expression.BinaryExpr(op, val, AstNode.intLiteral(0)));
            }
            case IFNULL -> {
                AstNode.Expression val = stack.isEmpty() ? AstNode.nullLiteral() : stack.pop();
                stack.push(new AstNode.Expression.BinaryExpr("==", val, AstNode.nullLiteral()));
            }
            case IFNONNULL -> {
                AstNode.Expression val = stack.isEmpty() ? AstNode.nullLiteral() : stack.pop();
                stack.push(new AstNode.Expression.BinaryExpr("!=", val, AstNode.nullLiteral()));
            }
            case GOTO, GOTO_W -> {}
            case TABLESWITCH, LOOKUPSWITCH -> {}

            case IRETURN, LRETURN, FRETURN, DRETURN, ARETURN ->
                statements.add(new AstNode.Statement.ReturnStmt(
                        stack.isEmpty() ? null : stack.pop()));
            case RETURN ->
                statements.add(new AstNode.Statement.ReturnStmt(null));
            case ATHROW ->
                statements.add(new AstNode.Statement.ThrowStmt(
                        stack.isEmpty() ? AstNode.nullLiteral() : stack.pop()));

            default -> {}
        }
    }

    private AstNode.Expression createVarExpr(Instruction inst, ClassFileParser.MethodEntry method) {
        String varName = getVarName(inst, method);
        String desc = getLocalDescriptor(inst, method);
        if ("this".equals(varName)) {
            return new AstNode.Expression.ThisExpr();
        }
        return new AstNode.Expression.VarExpr(varName, desc);
    }

    private void binOp(String op, Deque<AstNode.Expression> stack) {
        AstNode.Expression right = stack.isEmpty() ? AstNode.intLiteral(0) : stack.pop();
        AstNode.Expression left = stack.isEmpty() ? AstNode.intLiteral(0) : stack.pop();
        stack.push(new AstNode.Expression.BinaryExpr(op, left, right));
    }

    private List<AstNode.Expression> popStackArgs(Deque<AstNode.Expression> stack, String descriptor) {
        List<AstNode.Expression> args = new ArrayList<>();
        if (descriptor == null) return args;

        int parenEnd = descriptor.indexOf(')');
        if (parenEnd <= 1) return args;

        String sig = descriptor.substring(1, parenEnd);
        List<String> types = splitSignature(sig);
        List<AstNode.Expression> temp = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            if (stack.isEmpty()) {
                temp.add(new AstNode.Expression.VarExpr("arg" + i, types.get(i)));
            } else {
                temp.add(stack.pop());
            }
        }
        Collections.reverse(temp);
        args.addAll(temp);
        return args;
    }

    private List<String> splitSignature(String sig) {
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

    private String owner(int ridx) {
        String o = classParser.cpMethodOwner(ridx);
        return o != null ? o.replace('/', '.') : "?";
    }

    private String memberName(int ridx) {
        String n = classParser.cpMemberName(ridx);
        return n != null ? n : "?";
    }

    private String memberDesc(int ridx) {
        String d = classParser.cpMemberDescriptor(ridx);
        return d != null ? d : "?";
    }

    private String extractReturnType(String descriptor) {
        if (descriptor == null) return "void";
        int parenIdx = descriptor.indexOf(')');
        if (parenIdx < 0 || parenIdx + 1 >= descriptor.length()) return "void";
        return inferTypeFromDescriptor(descriptor.substring(parenIdx + 1));
    }

    private String getVarName(Instruction inst, ClassFileParser.MethodEntry method) {
        int index = getVarIndex(inst);
        if (index == 0 && !hasFlag(method.accessFlags(), 0x0008)) {
            return "this";
        }

        var code = method.code();
        if (code != null && code.attributes() != null) {
            Object lvtObj = code.attributes().get("LocalVariableTable");
            if (lvtObj instanceof ClassFileParser.LocalVar[] vars) {
                for (var v : vars) {
                    if (v.index() == index) return v.name();
                }
            }
        }

        return paramNameFromDescriptor(method.descriptor(), index, hasFlag(method.accessFlags(), 0x0008));
    }

    private String paramNameFromDescriptor(String descriptor, int index, boolean isStatic) {
        if (descriptor == null || descriptor.length() < 3) return "var" + index;
        String params = descriptor.substring(1, descriptor.indexOf(')'));
        if (params.isEmpty()) return "var" + index;

        int slot = isStatic ? 0 : 1;
        List<String> types = splitSignature(params);
        for (int i = 0; i < types.size(); i++) {
            if (slot == index) {
                String type = inferTypeFromDescriptor(types.get(i));
                String prefix = switch (type) {
                    case "int", "java.lang.Integer" -> "i";
                    case "long", "java.lang.Long" -> "l";
                    case "float", "java.lang.Float" -> "f";
                    case "double", "java.lang.Double" -> "d";
                    case "boolean", "java.lang.Boolean" -> "b";
                    case "byte", "java.lang.Byte" -> "b";
                    case "char", "java.lang.Character" -> "c";
                    case "short", "java.lang.Short" -> "s";
                    case "java.lang.String" -> "s";
                    default -> Character.toString(
                            java.lang.Character.toLowerCase(
                                    type.contains(".")
                                            ? type.substring(type.lastIndexOf('.') + 1).charAt(0)
                                            : type.charAt(0)));
                };
                return prefix + (i > 0 ? i : "");
            }
            slot += types.get(i).equals("J") || types.get(i).equals("D") ? 2 : 1;
        }
        return "var" + index;
    }

    private String getLocalDescriptor(Instruction inst, ClassFileParser.MethodEntry method) {
        int index = getVarIndex(inst);

        var code = method.code();
        if (code != null && code.attributes() != null) {
            Object lvtObj = code.attributes().get("LocalVariableTable");
            if (lvtObj instanceof ClassFileParser.LocalVar[] vars) {
                for (var v : vars) {
                    if (v.index() == index) return v.descriptor();
                }
            }
        }

        return switch (inst.opcode) {
            case ILOAD, ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3, ISTORE, ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3 -> "I";
            case LLOAD, LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3, LSTORE, LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3 -> "J";
            case FLOAD, FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3, FSTORE, FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3 -> "F";
            case DLOAD, DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3, DSTORE, DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3 -> "D";
            case ALOAD, ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3, ASTORE, ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3 -> "Ljava/lang/Object;";
            default -> "Ljava/lang/Object;";
        };
    }

    private int getVarIndex(Instruction inst) {
        return switch (inst.opcode) {
            case ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
                 ISTORE, LSTORE, FSTORE, DSTORE, ASTORE,
                 RET -> inst.operands.length > 0 ? inst.operands[0] & 0xFF : 0;
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

    public static String inferTypeFromDescriptor(String descriptor) {
        if (descriptor == null) return "java.lang.Object";
        return switch (descriptor) {
            case "I" -> "int";
            case "J" -> "long";
            case "F" -> "float";
            case "D" -> "double";
            case "Z" -> "boolean";
            case "B" -> "byte";
            case "C" -> "char";
            case "S" -> "short";
            case "V" -> "void";
            default -> {
                if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                    yield descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
                }
                if (descriptor.startsWith("[")) {
                    yield inferTypeFromDescriptor(descriptor.substring(1)) + "[]";
                }
                yield "java.lang.Object";
            }
        };
    }

    private boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
