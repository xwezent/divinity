package com.divinity.classfile;

import com.divinity.util.IoUtil;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

public final class ClassFileParser {

    private record BootstrapMethod(int methodRef, int[] arguments) {}

    public static final class ClassInfo {
        public int minorVersion;
        public int majorVersion;
        public int accessFlags;
        public String thisClass;
        public String superClass;
        public List<String> interfaces;
        public List<FieldEntry> fields;
        public List<MethodEntry> methods;
        public Map<String, Object> attributes;
        public String sourceFile;
        public String signature;
        public String nestHost;
        public List<String> nestMembers;
        public List<String> permittedSubclasses;
        public List<RecordComponent> recordComponents;
        public BootstrapMethod[] bootstrapMethods;
        public int javaVersion;

        public boolean isInterface() { return (accessFlags & 0x0200) != 0; }
        public boolean isEnum() { return (accessFlags & 0x4000) != 0; }
        public boolean isRecord() { return (accessFlags & 0x0010) != 0; }
        public boolean isAnnotation() { return (accessFlags & 0x2000) != 0; }

        public int javaVersion() {
            return majorVersion - 44;
        }

        public String packageName() {
            int idx = thisClass.lastIndexOf('/');
            return idx >= 0 ? thisClass.substring(0, idx).replace('/', '.') : "";
        }

        public String simpleName() {
            int idx = thisClass.lastIndexOf('/');
            return idx >= 0 ? thisClass.substring(idx + 1) : thisClass;
        }
    }

    public record FieldEntry(int accessFlags, String name, String descriptor,
                              String signature, Object constValue, Map<String, Object> attributes) {}

    public record MethodEntry(int accessFlags, String name, String descriptor,
                               String signature, String[] exceptions, CodeAttribute code,
                               Map<String, Object> attributes) {}

    public record CodeAttribute(int maxStack, int maxLocals,
                                 byte[] bytecode, ExceptionHandler[] exceptionTable,
                                 Map<String, Object> attributes) {}

    public record ExceptionHandler(int startPc, int endPc, int handlerPc, String catchType) {}

    public record RecordComponent(String name, String descriptor, String signature) {}

    public record LocalVar(int startPc, int length, String name, String descriptor, int index) {}

    private final ByteBuffer buf;
    private final ConstantPool.Entry[] constantPool;
    private final int cpCount;
    private final ClassInfo classInfo;

    public ClassFileParser(byte[] classData) throws IOException {
        this.buf = IoUtil.buffer(classData);
        int magic = buf.getInt();
        if (magic != 0xCAFEBABE) {
            throw new IOException("Invalid class file magic: " + Integer.toHexString(magic));
        }
        classInfo = new ClassInfo();
        classInfo.minorVersion = IoUtil.readU2(buf);
        classInfo.majorVersion = IoUtil.readU2(buf);

        cpCount = IoUtil.readU2(buf);
        constantPool = new ConstantPool.Entry[cpCount];
        parseConstantPool();

        classInfo.accessFlags = IoUtil.readU2(buf);
        classInfo.thisClass = cpClassName(IoUtil.readU2(buf));
        int superIdx = IoUtil.readU2(buf);
        classInfo.superClass = superIdx == 0 ? null : cpClassName(superIdx);

        int ifCount = IoUtil.readU2(buf);
        classInfo.interfaces = new ArrayList<>(ifCount);
        for (int i = 0; i < ifCount; i++) {
            classInfo.interfaces.add(cpClassName(IoUtil.readU2(buf)));
        }

        classInfo.fields = new ArrayList<>();
        int fieldCount = IoUtil.readU2(buf);
        for (int i = 0; i < fieldCount; i++) {
            classInfo.fields.add(parseField());
        }

        classInfo.methods = new ArrayList<>();
        int methodCount = IoUtil.readU2(buf);
        for (int i = 0; i < methodCount; i++) {
            classInfo.methods.add(parseMethod());
        }

        classInfo.attributes = parseAttributes();

        classInfo.sourceFile = (String) classInfo.attributes.get("SourceFile");
        classInfo.signature = (String) classInfo.attributes.get("Signature");
        classInfo.nestHost = (String) classInfo.attributes.get("NestHost");
        classInfo.javaVersion = classInfo.javaVersion();

        Object[] permitted = (Object[]) classInfo.attributes.get("PermittedSubclasses");
        if (permitted != null) {
            classInfo.permittedSubclasses = new ArrayList<>();
            for (Object p : permitted) classInfo.permittedSubclasses.add((String) p);
        }

        Object[] recordComps = (Object[]) classInfo.attributes.get("Record");
        if (recordComps != null) {
            classInfo.recordComponents = new ArrayList<>();
            for (Object rc : recordComps) classInfo.recordComponents.add((RecordComponent) rc);
        }
    }

    private void parseConstantPool() throws IOException {
        int i = 1;
        while (i < cpCount) {
            int tag = IoUtil.readU1(buf);
            switch (tag) {
                case 1 -> {
                    String val = readModifiedUtf8();
                    constantPool[i] = new ConstantPool.Entry.Utf8(i, val);
                }
                case 3 -> {
                    constantPool[i] = new ConstantPool.Entry.Int(i, buf.getInt());
                }
                case 4 -> {
                    constantPool[i] = new ConstantPool.Entry.Float(i, buf.getFloat());
                }
                case 5 -> {
                    long val = buf.getLong();
                    constantPool[i] = new ConstantPool.Entry.Long(i, val);
                    constantPool[i + 1] = null;
                    i++;
                }
                case 6 -> {
                    double val = buf.getDouble();
                    constantPool[i] = new ConstantPool.Entry.Double(i, val);
                    constantPool[i + 1] = null;
                    i++;
                }
                case 7 -> {
                    constantPool[i] = new ConstantPool.Entry.Class(i, IoUtil.readU2(buf));
                }
                case 8 -> {
                    constantPool[i] = new ConstantPool.Entry.Str(i, IoUtil.readU2(buf));
                }
                case 9 -> {
                    constantPool[i] = new ConstantPool.Entry.FieldRef(i, IoUtil.readU2(buf), IoUtil.readU2(buf));
                }
                case 10 -> {
                    constantPool[i] = new ConstantPool.Entry.MethodRef(i, IoUtil.readU2(buf), IoUtil.readU2(buf));
                }
                case 11 -> {
                    constantPool[i] = new ConstantPool.Entry.InterfaceMethodRef(i, IoUtil.readU2(buf), IoUtil.readU2(buf));
                }
                case 12 -> {
                    constantPool[i] = new ConstantPool.Entry.NameAndType(i, IoUtil.readU2(buf), IoUtil.readU2(buf));
                }
                case 15 -> {
                    constantPool[i] = new ConstantPool.Entry.MethodHandle(i, IoUtil.readU1(buf), IoUtil.readU2(buf));
                }
                case 16 -> {
                    constantPool[i] = new ConstantPool.Entry.MethodType(i, IoUtil.readU2(buf));
                }
                case 17 -> {
                    constantPool[i] = new ConstantPool.Entry.Dynamic(i, IoUtil.readU2(buf), IoUtil.readU2(buf));
                }
                case 18 -> {
                    constantPool[i] = new ConstantPool.Entry.InvokeDynamic(i, IoUtil.readU2(buf), IoUtil.readU2(buf));
                }
                case 19 -> {
                    constantPool[i] = new ConstantPool.Entry.Module(i, IoUtil.readU2(buf));
                }
                case 20 -> {
                    constantPool[i] = new ConstantPool.Entry.Package(i, IoUtil.readU2(buf));
                }
                default -> throw new IOException("Unknown constant pool tag: " + tag + " at index " + i);
            }
            i++;
        }
    }

    private String readModifiedUtf8() throws IOException {
        int length = IoUtil.readU2(buf);
        byte[] bytes = new byte[length];
        buf.get(bytes);
        try {
            return decodeModifiedUtf8(bytes);
        } catch (Exception e) {
            return safeDecodeUtf8(bytes);
        }
    }

    private String decodeModifiedUtf8(byte[] bytes) throws IOException {
        char[] chars = new char[bytes.length];
        int c = 0, b = 0;
        while (b < bytes.length) {
            int ch = bytes[b++] & 0xFF;
            if (ch < 0x80) {
                chars[c++] = (char) ch;
            } else if ((ch & 0xE0) == 0xC0) {
                if (b >= bytes.length) break;
                chars[c++] = (char) (((ch & 0x1F) << 6) | (bytes[b++] & 0x3F));
            } else if ((ch & 0xF0) == 0xE0) {
                if (b + 1 >= bytes.length) break;
                chars[c++] = (char) (((ch & 0x0F) << 12) | ((bytes[b++] & 0x3F) << 6) | (bytes[b++] & 0x3F));
            } else {
                chars[c++] = '\uFFFD';
            }
        }
        return new String(chars, 0, c);
    }

    private String safeDecodeUtf8(byte[] bytes) {
        try {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }

    public ConstantPool.Entry cp(int index) {
        if (index <= 0 || index >= cpCount) return null;
        return constantPool[index];
    }

    public String cpUtf8(int index) {
        ConstantPool.Entry e = cp(index);
        return e instanceof ConstantPool.Entry.Utf8 u ? u.value() : null;
    }

    public String cpClassName(int index) {
        ConstantPool.Entry e = cp(index);
        if (e instanceof ConstantPool.Entry.Class c) {
            return cpUtf8(c.nameIndex());
        }
        return null;
    }

    public String cpString(int index) {
        ConstantPool.Entry e = cp(index);
        if (e instanceof ConstantPool.Entry.Str s) {
            return cpUtf8(s.stringIndex());
        }
        return null;
    }

    public ConstantPool.Entry.NameAndType cpNameAndType(int index) {
        ConstantPool.Entry e = cp(index);
        return e instanceof ConstantPool.Entry.NameAndType nat ? nat : null;
    }

    public String cpMemberName(int refIndex) {
        ConstantPool.Entry e = cp(refIndex);
        if (e instanceof ConstantPool.Entry.FieldRef f) {
            ConstantPool.Entry.NameAndType nat = cpNameAndType(f.nameAndTypeIndex());
            return nat != null ? cpUtf8(nat.nameIndex()) : null;
        }
        if (e instanceof ConstantPool.Entry.MethodRef m) {
            ConstantPool.Entry.NameAndType nat = cpNameAndType(m.nameAndTypeIndex());
            return nat != null ? cpUtf8(nat.nameIndex()) : null;
        }
        if (e instanceof ConstantPool.Entry.InterfaceMethodRef m) {
            ConstantPool.Entry.NameAndType nat = cpNameAndType(m.nameAndTypeIndex());
            return nat != null ? cpUtf8(nat.nameIndex()) : null;
        }
        return null;
    }

    public String cpMemberDescriptor(int refIndex) {
        ConstantPool.Entry e = cp(refIndex);
        if (e instanceof ConstantPool.Entry.FieldRef f) {
            ConstantPool.Entry.NameAndType nat = cpNameAndType(f.nameAndTypeIndex());
            return nat != null ? cpUtf8(nat.descriptorIndex()) : null;
        }
        if (e instanceof ConstantPool.Entry.MethodRef m) {
            ConstantPool.Entry.NameAndType nat = cpNameAndType(m.nameAndTypeIndex());
            return nat != null ? cpUtf8(nat.descriptorIndex()) : null;
        }
        if (e instanceof ConstantPool.Entry.InterfaceMethodRef m) {
            ConstantPool.Entry.NameAndType nat = cpNameAndType(m.nameAndTypeIndex());
            return nat != null ? cpUtf8(nat.descriptorIndex()) : null;
        }
        return null;
    }

    public String cpMethodOwner(int refIndex) {
        ConstantPool.Entry e = cp(refIndex);
        return switch (e) {
            case ConstantPool.Entry.FieldRef f -> cpClassName(f.classIndex());
            case ConstantPool.Entry.MethodRef m -> cpClassName(m.classIndex());
            case ConstantPool.Entry.InterfaceMethodRef m -> cpClassName(m.classIndex());
            default -> null;
        };
    }

    private FieldEntry parseField() throws IOException {
        int flags = IoUtil.readU2(buf);
        String name = cpUtf8(IoUtil.readU2(buf));
        String desc = cpUtf8(IoUtil.readU2(buf));
        Map<String, Object> attrs = parseAttributes();
        return new FieldEntry(flags, name, desc,
                (String) attrs.get("Signature"),
                attrs.get("ConstantValue"),
                attrs);
    }

    private MethodEntry parseMethod() throws IOException {
        int flags = IoUtil.readU2(buf);
        String name = cpUtf8(IoUtil.readU2(buf));
        String desc = cpUtf8(IoUtil.readU2(buf));
        Map<String, Object> attrs = parseAttributes();
        CodeAttribute code = null;
        Object codeObj = attrs.get("Code");
        if (codeObj instanceof CodeAttribute ca) {
            code = ca;
            attrs.remove("Code");
        }
        return new MethodEntry(flags, name, desc,
                (String) attrs.get("Signature"),
                (String[]) attrs.get("Exceptions"),
                code, attrs);
    }

    private Map<String, Object> parseAttributes() throws IOException {
        int count = IoUtil.readU2(buf);
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String name = cpUtf8(IoUtil.readU2(buf));
            int len = IoUtil.readU4(buf);

            try {
                Object val = switch (name) {
                    case "SourceFile" -> cpUtf8(IoUtil.readU2(buf));
                    case "Signature" -> cpUtf8(IoUtil.readU2(buf));
                    case "NestHost" -> cpClassName(IoUtil.readU2(buf));
                    case "ConstantValue" -> parseConstantValue(IoUtil.readU2(buf));
                    case "Code" -> parseCodeAttribute();
                    case "Exceptions" -> {
                        int n = IoUtil.readU2(buf);
                        String[] exs = new String[n];
                        for (int j = 0; j < n; j++) exs[j] = cpClassName(IoUtil.readU2(buf));
                        yield exs;
                    }
                    case "LineNumberTable" -> {
                        int n = IoUtil.readU2(buf);
                        int[][] table = new int[n][2];
                        for (int j = 0; j < n; j++) {
                            table[j][0] = IoUtil.readU2(buf);
                            table[j][1] = IoUtil.readU2(buf);
                        }
                        yield table;
                    }
                    case "LocalVariableTable" -> {
                        int n = IoUtil.readU2(buf);
                        LocalVar[] vars = new LocalVar[n];
                        for (int j = 0; j < n; j++) {
                            vars[j] = new LocalVar(IoUtil.readU2(buf), IoUtil.readU2(buf),
                                    cpUtf8(IoUtil.readU2(buf)), cpUtf8(IoUtil.readU2(buf)), IoUtil.readU2(buf));
                        }
                        yield vars;
                    }
                    case "BootstrapMethods" -> {
                        int n = IoUtil.readU2(buf);
                        BootstrapMethod[] bms = new BootstrapMethod[n];
                        for (int j = 0; j < n; j++) {
                            int ref = IoUtil.readU2(buf);
                            int argCount = IoUtil.readU2(buf);
                            int[] args = new int[argCount];
                            for (int k = 0; k < argCount; k++) args[k] = IoUtil.readU2(buf);
                            bms[j] = new BootstrapMethod(ref, args);
                        }
                        classInfo.bootstrapMethods = bms;
                        yield bms;
                    }
                    case "InnerClasses" -> {
                        int n = IoUtil.readU2(buf);
                        String[] inners = new String[n];
                        for (int j = 0; j < n; j++) {
                            int innerClass = IoUtil.readU2(buf);
                            int outerClass = IoUtil.readU2(buf);
                            int innerName = IoUtil.readU2(buf);
                            int innerFlags = IoUtil.readU2(buf);
                            inners[j] = cpClassName(innerClass);
                        }
                        yield inners;
                    }
                    case "NestMembers" -> {
                        int n = IoUtil.readU2(buf);
                        String[] members = new String[n];
                        for (int j = 0; j < n; j++) members[j] = cpClassName(IoUtil.readU2(buf));
                        yield members;
                    }
                    case "PermittedSubclasses" -> {
                        int n = IoUtil.readU2(buf);
                        String[] permitted = new String[n];
                        for (int j = 0; j < n; j++) permitted[j] = cpClassName(IoUtil.readU2(buf));
                        yield permitted;
                    }
                    case "Record" -> {
                        int n = IoUtil.readU2(buf);
                        RecordComponent[] comps = new RecordComponent[n];
                        for (int j = 0; j < n; j++) {
                            String rName = cpUtf8(IoUtil.readU2(buf));
                            String rDesc = cpUtf8(IoUtil.readU2(buf));
                            int attrCount = IoUtil.readU2(buf);
                            String rSig = null;
                            for (int k = 0; k < attrCount; k++) {
                                String attrName = cpUtf8(IoUtil.readU2(buf));
                                int attrLen = IoUtil.readU4(buf);
                                if ("Signature".equals(attrName)) {
                                    rSig = cpUtf8(IoUtil.readU2(buf));
                                } else {
                                    buf.position(buf.position() + attrLen);
                                }
                            }
                            comps[j] = new RecordComponent(rName, rDesc, rSig);
                        }
                        yield comps;
                    }
                    default -> {
                        byte[] skipped = new byte[len];
                        buf.get(skipped);
                        yield skipped;
                    }
                };
                map.put(name, val);
            } catch (Exception e) {
                int remaining = len - Math.max(0, buf.position());
                if (remaining > 0) {
                    byte[] skip = new byte[remaining];
                    buf.get(skip);
                }
                map.put(name, new byte[0]);
            }
        }
        return map;
    }

    private Object parseConstantValue(int index) {
        ConstantPool.Entry e = cp(index);
        if (e instanceof ConstantPool.Entry.Str s) return cpUtf8(s.stringIndex());
        if (e instanceof ConstantPool.Entry.Int i) return i.value();
        if (e instanceof ConstantPool.Entry.Float f) return f.value();
        if (e instanceof ConstantPool.Entry.Long l) return l.value();
        if (e instanceof ConstantPool.Entry.Double d) return d.value();
        return null;
    }

    private CodeAttribute parseCodeAttribute() throws IOException {
        int maxStack = IoUtil.readU2(buf);
        int maxLocals = IoUtil.readU2(buf);
        int codeLen = IoUtil.readU4(buf);
        byte[] code = new byte[codeLen];
        buf.get(code);

        int excCount = IoUtil.readU2(buf);
        ExceptionHandler[] handlers = new ExceptionHandler[excCount];
        for (int i = 0; i < excCount; i++) {
            int start = IoUtil.readU2(buf);
            int end = IoUtil.readU2(buf);
            int handler = IoUtil.readU2(buf);
            int catchIdx = IoUtil.readU2(buf);
            String catchType = catchIdx == 0 ? null : cpClassName(catchIdx);
            handlers[i] = new ExceptionHandler(start, end, handler, catchType);
        }

        Map<String, Object> codeAttrs = parseAttributes();
        return new CodeAttribute(maxStack, maxLocals, code, handlers, codeAttrs);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        return (T) classInfo.attributes.get(name);
    }

    public ClassInfo getClassInfo() {
        return classInfo;
    }
}
