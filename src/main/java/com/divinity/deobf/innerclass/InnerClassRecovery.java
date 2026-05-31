package com.divinity.deobf.innerclass;

import com.divinity.classfile.ClassFileParser;
import java.util.*;

public final class InnerClassRecovery {

    private final ClassFileParser classParser;
    private final Map<String, InnerClassInfo> innerClasses;

    public InnerClassRecovery(ClassFileParser classParser) {
        this.classParser = classParser;
        this.innerClasses = new LinkedHashMap<>();
    }

    public void recover() {
        ClassFileParser.ClassInfo info = classParser.getClassInfo();

        // Inner classes information is not currently exposed in ClassInfo
        // This would need to be added to the ClassFileParser

        detectAnonymousClasses();
        detectLocalClasses();
    }

    private InnerClassType determineType(String innerName, String outerName, int accessFlags) {
        if (innerName == null || innerName.isEmpty()) {
            return InnerClassType.ANONYMOUS;
        }

        if (outerName == null || outerName.isEmpty()) {
            return InnerClassType.LOCAL;
        }

        if ((accessFlags & 0x0008) != 0) {
            return InnerClassType.STATIC_NESTED;
        }

        return InnerClassType.MEMBER;
    }

    private void detectAnonymousClasses() {
        String className = classParser.getClassInfo().thisClass;
        if (className != null && className.matches(".*\\$\\d+$")) {
            String outerClass = className.substring(0, className.lastIndexOf('$'));
            innerClasses.put(className, new InnerClassInfo(
                className, outerClass, InnerClassType.ANONYMOUS, 0
            ));
        }
    }

    private void detectLocalClasses() {
        String className = classParser.getClassInfo().thisClass;
        if (className != null && className.matches(".*\\$\\d+[A-Z].*$")) {
            String outerClass = className.substring(0, className.lastIndexOf('$'));
            innerClasses.put(className, new InnerClassInfo(
                className, outerClass, InnerClassType.LOCAL, 0
            ));
        }
    }

    public Map<String, InnerClassInfo> getInnerClasses() {
        return innerClasses;
    }

    public boolean isInnerClass(String className) {
        return innerClasses.containsKey(className);
    }

    public InnerClassInfo getInnerClassInfo(String className) {
        return innerClasses.get(className);
    }

    public String getSimpleName(String className) {
        InnerClassInfo info = innerClasses.get(className);
        if (info == null) return className;

        return switch (info.type()) {
            case ANONYMOUS -> "Anonymous";
            case LOCAL -> extractLocalClassName(className);
            case MEMBER, STATIC_NESTED -> extractMemberClassName(className);
        };
    }

    private String extractLocalClassName(String className) {
        int dollarIndex = className.lastIndexOf('$');
        if (dollarIndex >= 0 && dollarIndex + 1 < className.length()) {
            String suffix = className.substring(dollarIndex + 1);
            if (suffix.matches("\\d+.*")) {
                return suffix.replaceFirst("^\\d+", "");
            }
        }
        return className;
    }

    private String extractMemberClassName(String className) {
        int dollarIndex = className.lastIndexOf('$');
        if (dollarIndex >= 0 && dollarIndex + 1 < className.length()) {
            return className.substring(dollarIndex + 1);
        }
        return className;
    }

    public enum InnerClassType {
        MEMBER,
        STATIC_NESTED,
        LOCAL,
        ANONYMOUS
    }

    public record InnerClassInfo(String innerName, String outerName,
                                 InnerClassType type, int accessFlags) {
        public boolean isStatic() {
            return (accessFlags & 0x0008) != 0;
        }

        public boolean isPrivate() {
            return (accessFlags & 0x0002) != 0;
        }

        public boolean isProtected() {
            return (accessFlags & 0x0004) != 0;
        }

        public boolean isPublic() {
            return (accessFlags & 0x0001) != 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            if (isPublic()) sb.append("public ");
            else if (isProtected()) sb.append("protected ");
            else if (isPrivate()) sb.append("private ");

            if (isStatic()) sb.append("static ");

            sb.append(type.name().toLowerCase()).append(" class ");

            int lastDollar = innerName.lastIndexOf('$');
            if (lastDollar >= 0) {
                sb.append(innerName.substring(lastDollar + 1));
            } else {
                sb.append(innerName);
            }

            return sb.toString();
        }
    }
}
