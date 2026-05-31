package com.divinity.types;

import java.util.*;

public sealed interface JavaType {

    record Primitive(String name) implements JavaType {
        public static final Primitive INT = new Primitive("int");
        public static final Primitive LONG = new Primitive("long");
        public static final Primitive FLOAT = new Primitive("float");
        public static final Primitive DOUBLE = new Primitive("double");
        public static final Primitive BOOLEAN = new Primitive("boolean");
        public static final Primitive BYTE = new Primitive("byte");
        public static final Primitive CHAR = new Primitive("char");
        public static final Primitive SHORT = new Primitive("short");
        public static final Primitive VOID = new Primitive("void");

        @Override
        public String toString() { return name; }
    }

    record Class(String name, List<JavaType> typeArguments) implements JavaType {
        public Class(String name) {
            this(name, List.of());
        }

        @Override
        public String toString() {
            if (typeArguments.isEmpty()) return name;
            StringBuilder sb = new StringBuilder(name);
            sb.append("<");
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(typeArguments.get(i).toString());
            }
            sb.append(">");
            return sb.toString();
        }
    }

    record Array(JavaType elementType) implements JavaType {
        @Override
        public String toString() {
            return elementType.toString() + "[]";
        }
    }

    record TypeVariable(String name, List<JavaType> bounds) implements JavaType {
        public TypeVariable(String name) {
            this(name, List.of());
        }

        @Override
        public String toString() {
            if (bounds.isEmpty()) return name;
            return name + " extends " + String.join(" & ",
                    bounds.stream().map(JavaType::toString).toList());
        }
    }

    record Wildcard(JavaType bound, boolean isExtends) implements JavaType {
        @Override
        public String toString() {
            if (bound == null) return "?";
            return isExtends ? "? extends " + bound : "? super " + bound;
        }
    }

    record Null() implements JavaType {
        @Override
        public String toString() { return "null"; }
    }

    record Unknown() implements JavaType {
        @Override
        public String toString() { return "?"; }
    }

    record Union(List<JavaType> types) implements JavaType {
        @Override
        public String toString() {
            return String.join(" | ", types.stream().map(JavaType::toString).toList());
        }
    }

    record Intersection(List<JavaType> types) implements JavaType {
        @Override
        public String toString() {
            return String.join(" & ", types.stream().map(JavaType::toString).toList());
        }
    }

    static JavaType fromDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) return new Unknown();

        return switch (descriptor.charAt(0)) {
            case 'I' -> Primitive.INT;
            case 'J' -> Primitive.LONG;
            case 'F' -> Primitive.FLOAT;
            case 'D' -> Primitive.DOUBLE;
            case 'Z' -> Primitive.BOOLEAN;
            case 'B' -> Primitive.BYTE;
            case 'C' -> Primitive.CHAR;
            case 'S' -> Primitive.SHORT;
            case 'V' -> Primitive.VOID;
            case 'L' -> {
                int end = descriptor.indexOf(';');
                if (end < 0) yield new Unknown();
                String className = descriptor.substring(1, end).replace('/', '.');
                yield new Class(className);
            }
            case '[' -> new Array(fromDescriptor(descriptor.substring(1)));
            default -> new Unknown();
        };
    }

    static JavaType fromSignature(String signature) {
        if (signature == null || signature.isEmpty()) return new Unknown();

        if (signature.startsWith("L") && signature.contains("<")) {
            int genericStart = signature.indexOf('<');
            int classEnd = genericStart;
            String className = signature.substring(1, classEnd).replace('/', '.');

            List<JavaType> typeArgs = new ArrayList<>();
            int depth = 0;
            int start = genericStart + 1;

            for (int i = start; i < signature.length(); i++) {
                char c = signature.charAt(i);
                if (c == '<') depth++;
                else if (c == '>') {
                    if (depth == 0) {
                        if (i > start) {
                            String argSig = signature.substring(start, i);
                            typeArgs.add(fromSignature(argSig));
                        }
                        break;
                    }
                    depth--;
                } else if (c == ';' && depth == 0) {
                    String argSig = signature.substring(start, i + 1);
                    typeArgs.add(fromSignature(argSig));
                    start = i + 1;
                }
            }

            return new Class(className, typeArgs);
        }

        if (signature.startsWith("T") && signature.endsWith(";")) {
            String varName = signature.substring(1, signature.length() - 1);
            return new TypeVariable(varName);
        }

        if (signature.startsWith("+")) {
            return new Wildcard(fromSignature(signature.substring(1)), true);
        }

        if (signature.startsWith("-")) {
            return new Wildcard(fromSignature(signature.substring(1)), false);
        }

        if (signature.equals("*")) {
            return new Wildcard(null, true);
        }

        return fromDescriptor(signature);
    }

    default boolean isSubtypeOf(JavaType other) {
        if (this.equals(other)) return true;
        if (other instanceof Unknown) return true;
        if (this instanceof Null && other instanceof Class) return true;

        if (this instanceof Class thisClass && other instanceof Class otherClass) {
            if (thisClass.name().equals(otherClass.name())) {
                if (thisClass.typeArguments().size() == otherClass.typeArguments().size()) {
                    for (int i = 0; i < thisClass.typeArguments().size(); i++) {
                        JavaType thisArg = thisClass.typeArguments().get(i);
                        JavaType otherArg = otherClass.typeArguments().get(i);
                        if (!thisArg.equals(otherArg)) return false;
                    }
                    return true;
                }
            }

            if (isSubclass(thisClass.name(), otherClass.name())) {
                return true;
            }
        }

        if (this instanceof Array thisArray && other instanceof Array otherArray) {
            return thisArray.elementType().isSubtypeOf(otherArray.elementType());
        }

        return false;
    }

    private static boolean isSubclass(String subclass, String superclass) {
        if (subclass.equals(superclass)) return true;

        Map<String, String> hierarchy = Map.ofEntries(
            Map.entry("java.lang.String", "java.lang.Object"),
            Map.entry("java.lang.Integer", "java.lang.Number"),
            Map.entry("java.lang.Long", "java.lang.Number"),
            Map.entry("java.lang.Float", "java.lang.Number"),
            Map.entry("java.lang.Double", "java.lang.Number"),
            Map.entry("java.lang.Byte", "java.lang.Number"),
            Map.entry("java.lang.Short", "java.lang.Number"),
            Map.entry("java.lang.Number", "java.lang.Object"),
            Map.entry("java.util.ArrayList", "java.util.List"),
            Map.entry("java.util.LinkedList", "java.util.List"),
            Map.entry("java.util.HashSet", "java.util.Set"),
            Map.entry("java.util.TreeSet", "java.util.Set"),
            Map.entry("java.util.HashMap", "java.util.Map"),
            Map.entry("java.util.TreeMap", "java.util.Map")
        );

        String current = subclass;
        Set<String> visited = new HashSet<>();

        while (current != null && visited.add(current)) {
            if (current.equals(superclass)) return true;
            current = hierarchy.get(current);
        }

        return false;
    }

    default JavaType commonSupertype(JavaType other) {
        if (this.equals(other)) return this;
        if (this instanceof Unknown || other instanceof Unknown) return new Unknown();
        if (this instanceof Null) return other;
        if (other instanceof Null) return this;

        if (this instanceof Class thisClass && other instanceof Class otherClass) {
            if (thisClass.name().equals(otherClass.name())) {
                return thisClass;
            }
            return new Class("java.lang.Object");
        }

        if (this instanceof Primitive && other instanceof Primitive) {
            return new Primitive("int");
        }

        return new Class("java.lang.Object");
    }
}
