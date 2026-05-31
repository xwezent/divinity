package com.divinity.writer;

import com.divinity.ast.AstNode;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

public final class JavaSourceWriter {

    private final ImportCollector importCollector;
    private final String packageName;
    private final String className;

    public JavaSourceWriter(String packageName, String className) {
        this.importCollector = new ImportCollector();
        this.packageName = packageName;
        this.className = className;
    }

    public String write(AstNode.ClassNode classNode) {
        StringBuilder sb = new StringBuilder();

        if (classNode.sourceFile() != null) {
            sb.append("// Decompiled by Divinity v1.0.0\n");
            sb.append("// Source: ").append(classNode.sourceFile()).append("\n\n");
        } else {
            sb.append("// Decompiled by Divinity v1.0.0\n\n");
        }

        collectImports(classNode);

        if (packageName != null && !packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }

        List<String> imports = importCollector.getImports(packageName);
        if (!imports.isEmpty()) {
            for (String imp : imports) {
                sb.append("import ").append(imp).append(";\n");
            }
            sb.append('\n');
        }

        writeClassAnnotations(classNode.accessFlags(), sb);

        String modifiers = modifiers(classNode.accessFlags());
        String typeKeyword = classNode.isInterface() ? "interface"
                : classNode.isEnum() ? "enum"
                : classNode.isRecord() ? "record"
                : classNode.isAnnotation() ? "@interface"
                : "class";

        sb.append(modifiers.isEmpty() ? "" : modifiers + " ").append(typeKeyword);
        sb.append(' ').append(className);

        if (classNode.isRecord() && classNode.recordComponents() != null) {
            sb.append('(');
            for (int i = 0; i < classNode.recordComponents().size(); i++) {
                if (i > 0) sb.append(", ");
                var rc = classNode.recordComponents().get(i);
                sb.append(inferTypeFromDescriptor(rc.descriptor()));
                sb.append(' ').append(rc.name());
            }
            sb.append(')');
        }

        String superName = classNode.superName();
        if (superName != null && !superName.equals("java.lang.Object") && !classNode.isInterface()
                && !classNode.isEnum() && !classNode.isRecord() && !classNode.isAnnotation()) {
            sb.append(" extends ").append(shortName(superName));
        }

        if (classNode.interfaces() != null && !classNode.interfaces().isEmpty()) {
            sb.append(classNode.isInterface() ? " extends " : " implements ");
            for (int i = 0; i < classNode.interfaces().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(shortName(classNode.interfaces().get(i)));
            }
        }

        if (classNode.permittedSubclasses() != null && !classNode.permittedSubclasses().isEmpty()) {
            sb.append("\n    permits ");
            for (int i = 0; i < classNode.permittedSubclasses().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(shortName(classNode.permittedSubclasses().get(i)));
            }
        }

        sb.append(" {\n");

        if (classNode.fields() != null) {
            for (var field : classNode.fields()) {
                writeField(field, sb);
            }
        }

        if (!classNode.fields().isEmpty() && !classNode.methods().isEmpty()) {
            sb.append('\n');
        }

        if (classNode.methods() != null) {
            for (int i = 0; i < classNode.methods().size(); i++) {
                if (i > 0) sb.append('\n');
                writeMethod(classNode.methods().get(i), sb, classNode);
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private void collectImports(AstNode.ClassNode classNode) {
        if (classNode.superName() != null && !classNode.superName().equals("java.lang.Object")) {
            importCollector.add(classNode.superName());
        }
        if (classNode.interfaces() != null) {
            for (String iface : classNode.interfaces()) {
                importCollector.add(iface);
            }
        }
        if (classNode.permittedSubclasses() != null) {
            for (String p : classNode.permittedSubclasses()) {
                importCollector.add(p);
            }
        }
        for (var field : classNode.fields()) {
            importCollector.addDescriptors(field.descriptor());
        }
        for (var method : classNode.methods()) {
            importCollector.addDescriptors(method.descriptor());
            if (method.exceptions() != null) {
                for (String ex : method.exceptions()) {
                    importCollector.add(ex);
                }
            }
        }
    }

    private void writeField(AstNode.FieldNode field, StringBuilder sb) {
        String mods = modifiers(field.accessFlags());
        String type = inferTypeFromDescriptor(field.descriptor());
        sb.append("    ");
        if (!mods.isEmpty()) sb.append(mods).append(' ');
        sb.append(shortName(type)).append(' ').append(field.name());
        if (field.constValue() != null) {
            sb.append(" = ");
            writeLiteral(field.constValue(), sb);
        }
        sb.append(";\n");
    }

    private void writeMethod(AstNode.MethodNode method, StringBuilder sb, AstNode.ClassNode classNode) {
        String mods = modifiers(method.accessFlags());
        String returnType = extractReturnType(method.descriptor());
        String name = method.name();

        if (name.equals("<clinit>")) {
            sb.append("    static {\n");
            writeBody(method.body(), sb, "        ");
            sb.append("    }\n");
            return;
        }
        if (name.equals("<init>")) {
            sb.append("    ");
            if (!mods.isEmpty()) sb.append(mods).append(' ');
            sb.append(className).append('(');
            writeParameters(method.descriptor(), sb);
            sb.append(")");
            if (method.exceptions() != null && !method.exceptions().isEmpty()) {
                sb.append(" throws ");
                for (int i = 0; i < method.exceptions().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(shortName(method.exceptions().get(i).replace('/', '.')));
                }
            }
            sb.append(" {\n");
            writeBody(method.body(), sb, "        ");
            sb.append("    }\n");
            return;
        }

        sb.append("    ");
        if (!mods.isEmpty()) sb.append(mods).append(' ');
        sb.append(shortName(returnType)).append(' ').append(name).append('(');
        writeParameters(method.descriptor(), sb);
        sb.append(')');
        if (method.exceptions() != null && !method.exceptions().isEmpty()) {
            sb.append(" throws ");
            for (int i = 0; i < method.exceptions().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(shortName(method.exceptions().get(i).replace('/', '.')));
            }
        }
        sb.append(" {\n");
        writeBody(method.body(), sb, "        ");
        sb.append("    }\n");
    }

    private void writeBody(List<AstNode.Statement> body, StringBuilder sb, String indent) {
        if (body == null || body.isEmpty()) {
            sb.append(indent).append("// method body\n");
            return;
        }
        for (AstNode.Statement stmt : body) {
            writeStatement(stmt, sb, indent);
        }
    }

    private void writeStatement(AstNode.Statement stmt, StringBuilder sb, String indent) {
        switch (stmt) {
            case AstNode.Statement.ReturnStmt rs -> {
                sb.append(indent).append("return");
                if (rs.value() != null) {
                    sb.append(' ');
                    writeExpr(rs.value(), sb);
                }
                sb.append(";\n");
            }
            case AstNode.Statement.ThrowStmt ts -> {
                sb.append(indent).append("throw ");
                writeExpr(ts.exception(), sb);
                sb.append(";\n");
            }
            case AstNode.Statement.ExpressionStmt es -> {
                sb.append(indent);
                writeExpr(es.expr(), sb);
                sb.append(";\n");
            }
            case AstNode.Statement.VarDeclStmt vd -> {
                sb.append(indent).append(shortName(vd.type())).append(' ').append(vd.name());
                if (vd.initializer() != null) {
                    sb.append(" = ");
                    writeExpr(vd.initializer(), sb);
                }
                sb.append(";\n");
            }
            case AstNode.Statement.IfStmt is -> {
                sb.append(indent).append("if (");
                writeExpr(is.condition(), sb);
                sb.append(") {\n");
                writeStatement(is.thenBranch(), sb, indent + "    ");
                sb.append(indent).append("}");
                if (is.elseBranch() != null && !(is.elseBranch() instanceof AstNode.Statement.EmptyStmt)) {
                    sb.append(" else {\n");
                    writeStatement(is.elseBranch(), sb, indent + "    ");
                    sb.append(indent).append("}");
                }
                sb.append('\n');
            }
            case AstNode.Statement.WhileStmt ws -> {
                if (ws.isDoWhile()) {
                    sb.append(indent).append("do {\n");
                    writeStatement(ws.body(), sb, indent + "    ");
                    sb.append(indent).append("} while (");
                    writeExpr(ws.condition(), sb);
                    sb.append(");\n");
                } else {
                    sb.append(indent).append("while (");
                    writeExpr(ws.condition(), sb);
                    sb.append(") {\n");
                    writeStatement(ws.body(), sb, indent + "    ");
                    sb.append(indent).append("}\n");
                }
            }
            case AstNode.Statement.SwitchStmt sw -> {
                sb.append(indent).append("switch (");
                writeExpr(sw.selector(), sb);
                sb.append(") {\n");
                if (sw.cases() != null) {
                    for (var c : sw.cases()) {
                        for (var val : c.matchValues()) {
                            sb.append(indent).append("    case ");
                            writeExpr(val, sb);
                            sb.append(":\n");
                        }
                        writeStatement(c.body(), sb, indent + "        ");
                        sb.append(indent).append("        break;\n");
                    }
                }
                if (sw.defaultCase() != null) {
                    sb.append(indent).append("    default:\n");
                    writeStatement(sw.defaultCase(), sb, indent + "        ");
                }
                sb.append(indent).append("}\n");
            }
            case AstNode.Statement.BlockStmt bs -> {
                for (var s : bs.statements()) {
                    writeStatement(s, sb, indent);
                }
            }
            default -> {}
        }
    }

    private void writeExpr(AstNode.Expression expr, StringBuilder sb) {
        switch (expr) {
            case AstNode.Expression.ThisExpr e -> sb.append("this");
            case AstNode.Expression.SuperExpr e -> sb.append("super");
            case AstNode.Expression.NullExpr e -> sb.append("null");
            case AstNode.Expression.LiteralExpr le -> writeLiteral(le.value(), sb);
            case AstNode.Expression.VarExpr ve -> sb.append(ve.name());
            case AstNode.Expression.BinaryExpr be -> {
                sb.append('(');
                writeExpr(be.left(), sb);
                sb.append(' ').append(be.operator()).append(' ');
                writeExpr(be.right(), sb);
                sb.append(')');
            }
            case AstNode.Expression.UnaryExpr ue -> {
                sb.append(ue.operator());
                writeExpr(ue.operand(), sb);
            }
            case AstNode.Expression.CastExpr ce -> {
                sb.append("((").append(shortName(ce.targetType())).append(") ");
                writeExpr(ce.operand(), sb);
                sb.append(')');
            }
            case AstNode.Expression.NewExpr ne -> {
                if (ne.isArray()) {
                    sb.append("new ").append(shortName(ne.type())).append("[0]");
                } else {
                    sb.append("new ").append(shortName(ne.type())).append("(");
                    writeArgs(ne.args(), sb);
                    sb.append(')');
                }
            }
            case AstNode.Expression.NewArrayExpr na -> {
                sb.append("new ").append(shortName(na.elementType())).append('[');
                writeExpr(na.size(), sb);
                sb.append(']');
            }
            case AstNode.Expression.MethodCallExpr mc -> {
                if ("<init>".equals(mc.name())) {
                    if (mc.object() instanceof AstNode.Expression.ThisExpr) {
                        sb.append("super");
                    } else if (mc.object() instanceof AstNode.Expression.NewExpr ne) {
                        sb.append("new ").append(shortName(ne.type()));
                    } else {
                        writeExpr(mc.object(), sb);
                    }
                } else {
                    writeExpr(mc.object(), sb);
                    sb.append('.');
                }
                if ("<init>".equals(mc.name())) {
                    sb.append('(');
                } else {
                    sb.append(mc.name()).append('(');
                }
                writeArgs(mc.args(), sb);
                sb.append(')');
            }
            case AstNode.Expression.StaticCallExpr sc -> {
                sb.append(shortName(sc.ownerClass())).append('.').append(sc.name()).append('(');
                writeArgs(sc.args(), sb);
                sb.append(')');
            }
            case AstNode.Expression.StaticFieldExpr sf -> {
                sb.append(shortName(sf.ownerClass())).append('.').append(sf.name());
            }
            case AstNode.Expression.FieldAccessExpr fa -> {
                writeExpr(fa.object(), sb);
                sb.append('.').append(fa.name());
            }
            case AstNode.Expression.InstanceOfExpr io -> {
                writeExpr(io.operand(), sb);
                sb.append(" instanceof ").append(shortName(io.targetType()));
            }
            case AstNode.Expression.AssignExpr ass -> {
                writeExpr(ass.target(), sb);
                sb.append(" = ");
                writeExpr(ass.value(), sb);
            }
            case AstNode.Expression.ArrayAccessExpr aa -> {
                writeExpr(aa.array(), sb);
                sb.append('[');
                writeExpr(aa.index(), sb);
                sb.append(']');
            }
            default -> {}
        }
    }

    private void writeLiteral(Object val, StringBuilder sb) {
        if (val == null) {
            sb.append("null");
        } else if (val instanceof String s) {
            sb.append('"').append(escapeString(s)).append('"');
        } else if (val instanceof Boolean b) {
            sb.append(b);
        } else if (val instanceof Character c) {
            sb.append('\'').append(escapeChar(c)).append('\'');
        } else if (val instanceof Float f) {
            sb.append(f).append('f');
        } else if (val instanceof Double d) {
            sb.append(d);
        } else if (val instanceof Long l) {
            sb.append(l).append('L');
        } else {
            sb.append(val);
        }
    }

    private String escapeString(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                default -> {
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private String escapeChar(char c) {
        return switch (c) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\\' -> "\\\\";
            case '\'' -> "\\'";
            default -> c < 32 || c > 126 ? String.format("\\u%04x", (int) c) : String.valueOf(c);
        };
    }

    private void writeArgs(List<AstNode.Expression> args, StringBuilder sb) {
        if (args == null) return;
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            if (args.get(i) != null) {
                writeExpr(args.get(i), sb);
            }
        }
    }

    private void writeParameters(String descriptor, StringBuilder sb) {
        if (descriptor == null || descriptor.length() < 3) return;
        String params = descriptor.substring(1, descriptor.indexOf(')'));
        if (params.isEmpty()) return;

        List<String> types = splitSignature(params);
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) sb.append(", ");
            String type = inferTypeFromDescriptor(types.get(i));
            String pName = inferParamName(type, i);
            sb.append(shortName(type)).append(' ').append(pName);
        }
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

    private String inferParamName(String type, int idx) {
        String base = switch (type) {
            case "int", "java.lang.Integer" -> "i";
            case "long", "java.lang.Long" -> "l";
            case "float", "java.lang.Float" -> "f";
            case "double", "java.lang.Double" -> "d";
            case "boolean", "java.lang.Boolean" -> "b";
            case "java.lang.String" -> "s";
            default -> {
                String shortName = shortName(type);
                yield Character.toLowerCase(shortName.charAt(0)) + "";
            }
        };
        return base + (idx > 0 ? idx : "");
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
                    yield descriptor.substring(1, descriptor.length() - 1);
                }
                if (descriptor.startsWith("[")) {
                    yield inferTypeFromDescriptor(descriptor.substring(1)) + "[]";
                }
                yield "java.lang.Object";
            }
        };
    }

    private String extractReturnType(String descriptor) {
        if (descriptor == null) return "void";
        int parenIdx = descriptor.indexOf(')');
        if (parenIdx < 0 || parenIdx + 1 >= descriptor.length()) return "void";
        return inferTypeFromDescriptor(descriptor.substring(parenIdx + 1));
    }

    public static String modifiers(int flags) {
        List<String> parts = new ArrayList<>();
        if ((flags & 0x0001) != 0) parts.add("public");
        else if ((flags & 0x0002) != 0) parts.add("private");
        else if ((flags & 0x0004) != 0) parts.add("protected");
        if ((flags & 0x0008) != 0) parts.add("static");
        if ((flags & 0x0010) != 0) parts.add("final");
        if ((flags & 0x0040) != 0) parts.add("volatile");
        if ((flags & 0x0080) != 0) parts.add("transient");
        if ((flags & 0x0100) != 0) parts.add("native");
        if ((flags & 0x0400) != 0) parts.add("abstract");
        if ((flags & 0x0800) != 0) parts.add("strictfp");
        if ((flags & 0x1000) != 0) parts.add("synthetic");
        return String.join(" ", parts);
    }

    private void writeClassAnnotations(int flags, StringBuilder sb) {
        if ((flags & 0x2000) != 0) {
        }
    }

    public static String shortName(String fullName) {
        if (fullName == null) return "Object";
        if (!fullName.contains(".") && !fullName.contains("/")) return fullName;
        String name = fullName.replace('/', '.');
        if (name.startsWith("java.lang.")) {
            String simple = name.substring(10);
            if (simple.equals("Object") || simple.equals("String") || simple.equals("Integer")
                    || simple.equals("Long") || simple.equals("Float") || simple.equals("Double")
                    || simple.equals("Boolean") || simple.equals("Byte") || simple.equals("Short")
                    || simple.equals("Character") || simple.equals("Void") || simple.equals("Class")
                    || simple.equals("Enum") || simple.equals("Throwable") || simple.equals("Exception")
                    || simple.equals("RuntimeException") || simple.equals("Error")
                    || simple.equals("Iterable") || simple.equals("AutoCloseable")
                    || simple.equals("Comparable") || simple.equals("CharSequence")
                    || simple.equals("Number") || simple.equals("System") || simple.equals("Math")
                    || simple.equals("StringBuilder") || simple.equals("StringBuffer")
                    || simple.equals("Override") || simple.equals("Deprecated") || simple.equals("SuppressWarnings")
                    || simple.equals("FunctionalInterface") || simple.equals("SafeVarargs")) {
                return simple;
            }
        }
        return name;
    }
}
