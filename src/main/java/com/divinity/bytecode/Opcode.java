package com.divinity.bytecode;

public enum Opcode {
    NOP(0x00, 0),
    ACONST_NULL(0x01, 0),
    ICONST_M1(0x02, 0),
    ICONST_0(0x03, 0),
    ICONST_1(0x04, 0),
    ICONST_2(0x05, 0),
    ICONST_3(0x06, 0),
    ICONST_4(0x07, 0),
    ICONST_5(0x08, 0),
    LCONST_0(0x09, 0),
    LCONST_1(0x0a, 0),
    FCONST_0(0x0b, 0),
    FCONST_1(0x0c, 0),
    FCONST_2(0x0d, 0),
    DCONST_0(0x0e, 0),
    DCONST_1(0x0f, 0),
    BIPUSH(0x10, 1),
    SIPUSH(0x11, 2),
    LDC(0x12, 1),
    LDC_W(0x13, 2),
    LDC2_W(0x14, 2),
    ILOAD(0x15, 1),
    LLOAD(0x16, 1),
    FLOAD(0x17, 1),
    DLOAD(0x18, 1),
    ALOAD(0x19, 1),
    ILOAD_0(0x1a, 0),
    ILOAD_1(0x1b, 0),
    ILOAD_2(0x1c, 0),
    ILOAD_3(0x1d, 0),
    LLOAD_0(0x1e, 0),
    LLOAD_1(0x1f, 0),
    LLOAD_2(0x20, 0),
    LLOAD_3(0x21, 0),
    FLOAD_0(0x22, 0),
    FLOAD_1(0x23, 0),
    FLOAD_2(0x24, 0),
    FLOAD_3(0x25, 0),
    DLOAD_0(0x26, 0),
    DLOAD_1(0x27, 0),
    DLOAD_2(0x28, 0),
    DLOAD_3(0x29, 0),
    ALOAD_0(0x2a, 0),
    ALOAD_1(0x2b, 0),
    ALOAD_2(0x2c, 0),
    ALOAD_3(0x2d, 0),
    IALOAD(0x2e, 0),
    LALOAD(0x2f, 0),
    FALOAD(0x30, 0),
    DALOAD(0x31, 0),
    AALOAD(0x32, 0),
    BALOAD(0x33, 0),
    CALOAD(0x34, 0),
    SALOAD(0x35, 0),
    ISTORE(0x36, 1),
    LSTORE(0x37, 1),
    FSTORE(0x38, 1),
    DSTORE(0x39, 1),
    ASTORE(0x3a, 1),
    ISTORE_0(0x3b, 0),
    ISTORE_1(0x3c, 0),
    ISTORE_2(0x3d, 0),
    ISTORE_3(0x3e, 0),
    LSTORE_0(0x3f, 0),
    LSTORE_1(0x40, 0),
    LSTORE_2(0x41, 0),
    LSTORE_3(0x42, 0),
    FSTORE_0(0x43, 0),
    FSTORE_1(0x44, 0),
    FSTORE_2(0x45, 0),
    FSTORE_3(0x46, 0),
    DSTORE_0(0x47, 0),
    DSTORE_1(0x48, 0),
    DSTORE_2(0x49, 0),
    DSTORE_3(0x4a, 0),
    ASTORE_0(0x4b, 0),
    ASTORE_1(0x4c, 0),
    ASTORE_2(0x4d, 0),
    ASTORE_3(0x4e, 0),
    IASTORE(0x4f, 0),
    LASTORE(0x50, 0),
    FASTORE(0x51, 0),
    DASTORE(0x52, 0),
    AASTORE(0x53, 0),
    BASTORE(0x54, 0),
    CASTORE(0x55, 0),
    SASTORE(0x56, 0),
    POP(0x57, 0),
    POP2(0x58, 0),
    DUP(0x59, 0),
    DUP_X1(0x5a, 0),
    DUP_X2(0x5b, 0),
    DUP2(0x5c, 0),
    DUP2_X1(0x5d, 0),
    DUP2_X2(0x5e, 0),
    SWAP(0x5f, 0),
    IADD(0x60, 0),
    LADD(0x61, 0),
    FADD(0x62, 0),
    DADD(0x63, 0),
    ISUB(0x64, 0),
    LSUB(0x65, 0),
    FSUB(0x66, 0),
    DSUB(0x67, 0),
    IMUL(0x68, 0),
    LMUL(0x69, 0),
    FMUL(0x6a, 0),
    DMUL(0x6b, 0),
    IDIV(0x6c, 0),
    LDIV(0x6d, 0),
    FDIV(0x6e, 0),
    DDIV(0x6f, 0),
    IREM(0x70, 0),
    LREM(0x71, 0),
    FREM(0x72, 0),
    DREM(0x73, 0),
    INEG(0x74, 0),
    LNEG(0x75, 0),
    FNEG(0x76, 0),
    DNEG(0x77, 0),
    ISHL(0x78, 0),
    LSHL(0x79, 0),
    ISHR(0x7a, 0),
    LSHR(0x7b, 0),
    IUSHR(0x7c, 0),
    LUSHR(0x7d, 0),
    IAND(0x7e, 0),
    LAND(0x7f, 0),
    IOR(0x80, 0),
    LOR(0x81, 0),
    IXOR(0x82, 0),
    LXOR(0x83, 0),
    IINC(0x84, 2),
    I2L(0x85, 0),
    I2F(0x86, 0),
    I2D(0x87, 0),
    L2I(0x88, 0),
    L2F(0x89, 0),
    L2D(0x8a, 0),
    F2I(0x8b, 0),
    F2L(0x8c, 0),
    F2D(0x8d, 0),
    D2I(0x8e, 0),
    D2L(0x8f, 0),
    D2F(0x90, 0),
    I2B(0x91, 0),
    I2C(0x92, 0),
    I2S(0x93, 0),
    LCMP(0x94, 0),
    FCMPL(0x95, 0),
    FCMPG(0x96, 0),
    DCMPL(0x97, 0),
    DCMPG(0x98, 0),
    IFEQ(0x99, 2),
    IFNE(0x9a, 2),
    IFLT(0x9b, 2),
    IFGE(0x9c, 2),
    IFGT(0x9d, 2),
    IFLE(0x9e, 2),
    IF_ICMPEQ(0x9f, 2),
    IF_ICMPNE(0xa0, 2),
    IF_ICMPLT(0xa1, 2),
    IF_ICMPGE(0xa2, 2),
    IF_ICMPGT(0xa3, 2),
    IF_ICMPLE(0xa4, 2),
    IF_ACMPEQ(0xa5, 2),
    IF_ACMPNE(0xa6, 2),
    GOTO(0xa7, 2),
    JSR(0xa8, 2),
    RET(0xa9, 1),
    TABLESWITCH(0xaa, -1),
    LOOKUPSWITCH(0xab, -1),
    IRETURN(0xac, 0),
    LRETURN(0xad, 0),
    FRETURN(0xae, 0),
    DRETURN(0xaf, 0),
    ARETURN(0xb0, 0),
    RETURN(0xb1, 0),
    GETSTATIC(0xb2, 2),
    PUTSTATIC(0xb3, 2),
    GETFIELD(0xb4, 2),
    PUTFIELD(0xb5, 2),
    INVOKEVIRTUAL(0xb6, 2),
    INVOKESPECIAL(0xb7, 2),
    INVOKESTATIC(0xb8, 2),
    INVOKEINTERFACE(0xb9, 4),
    INVOKEDYNAMIC(0xba, 4),
    NEW(0xbb, 2),
    NEWARRAY(0xbc, 1),
    ANEWARRAY(0xbd, 2),
    ARRAYLENGTH(0xbe, 0),
    ATHROW(0xbf, 0),
    CHECKCAST(0xc0, 2),
    INSTANCEOF(0xc1, 2),
    MONITORENTER(0xc2, 0),
    MONITOREXIT(0xc3, 0),
    WIDE(0xc4, -1),
    MULTIANEWARRAY(0xc5, 3),
    IFNULL(0xc6, 2),
    IFNONNULL(0xc7, 2),
    GOTO_W(0xc8, 4),
    JSR_W(0xc9, 4),
    BREAKPOINT(0xca, 0),
    IMPDEP1(0xfe, 0),
    IMPDEP2(0xff, 0);

    public final int opcode;
    public final int operandSize;

    private static final Opcode[] LOOKUP = new Opcode[256];

    static {
        for (Opcode o : values()) {
            if (o.opcode >= 0 && o.opcode < 256) {
                LOOKUP[o.opcode] = o;
            }
        }
    }

    Opcode(int opcode, int operandSize) {
        this.opcode = opcode;
        this.operandSize = operandSize;
    }

    public static Opcode fromByte(int b) {
        return LOOKUP[b & 0xFF];
    }

    public boolean isBranch() {
        return switch (this) {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                 IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL,
                 GOTO, GOTO_W, JSR, JSR_W,
                 TABLESWITCH, LOOKUPSWITCH -> true;
            default -> false;
        };
    }

    public boolean isUnconditionalBranch() {
        return switch (this) {
            case GOTO, GOTO_W, TABLESWITCH, LOOKUPSWITCH -> true;
            default -> false;
        };
    }

    public boolean isConditionalBranch() {
        return isBranch() && !isUnconditionalBranch();
    }

    public boolean isReturn() {
        return switch (this) {
            case IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN -> true;
            default -> false;
        };
    }

    public boolean isThrow() {
        return this == ATHROW;
    }

    public boolean isStore() {
        return switch (this) {
            case ISTORE, ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3,
                 LSTORE, LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3,
                 FSTORE, FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3,
                 DSTORE, DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3,
                 ASTORE, ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3,
                 IASTORE, LASTORE, FASTORE, DASTORE, AASTORE,
                 BASTORE, CASTORE, SASTORE,
                 PUTFIELD, PUTSTATIC -> true;
            default -> false;
        };
    }

    public boolean isInvoke() {
        return switch (this) {
            case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC,
                 INVOKEINTERFACE, INVOKEDYNAMIC -> true;
            default -> false;
        };
    }

    public boolean isConstantLoad() {
        return switch (this) {
            case ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2,
                 ICONST_3, ICONST_4, ICONST_5,
                 LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2,
                 DCONST_0, DCONST_1,
                 BIPUSH, SIPUSH, LDC, LDC_W, LDC2_W -> true;
            default -> false;
        };
    }
}
