package com.divinity.classfile;

public final class ConstantPool {

    private ConstantPool() {}

    public sealed interface Entry {
        int index();

        record Utf8(int index, java.lang.String value) implements Entry {}
        record Int(int index, int value) implements Entry {}
        record Float(int index, float value) implements Entry {}
        record Long(int index, long value) implements Entry {}
        record Double(int index, double value) implements Entry {}
        record Class(int index, int nameIndex) implements Entry {}
        record Str(int index, int stringIndex) implements Entry {}
        record FieldRef(int index, int classIndex, int nameAndTypeIndex) implements Entry {}
        record MethodRef(int index, int classIndex, int nameAndTypeIndex) implements Entry {}
        record InterfaceMethodRef(int index, int classIndex, int nameAndTypeIndex) implements Entry {}
        record NameAndType(int index, int nameIndex, int descriptorIndex) implements Entry {}
        record MethodHandle(int index, int referenceKind, int referenceIndex) implements Entry {}
        record MethodType(int index, int descriptorIndex) implements Entry {}
        record Dynamic(int index, int bootstrapMethodAttrIndex, int nameAndTypeIndex) implements Entry {}
        record InvokeDynamic(int index, int bootstrapMethodAttrIndex, int nameAndTypeIndex) implements Entry {}
        record Module(int index, int nameIndex) implements Entry {}
        record Package(int index, int nameIndex) implements Entry {}
    }
}
