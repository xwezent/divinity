package com.divinity.deobf.annotation;

import com.divinity.classfile.ClassFileParser;
import java.util.*;

public final class AnnotationReconstructor {

    private final ClassFileParser classParser;
    private final Map<String, List<AnnotationInfo>> classAnnotations;
    private final Map<String, Map<String, List<AnnotationInfo>>> methodAnnotations;
    private final Map<String, Map<String, List<AnnotationInfo>>> fieldAnnotations;

    public AnnotationReconstructor(ClassFileParser classParser) {
        this.classParser = classParser;
        this.classAnnotations = new LinkedHashMap<>();
        this.methodAnnotations = new LinkedHashMap<>();
        this.fieldAnnotations = new LinkedHashMap<>();
    }

    public void reconstruct() {
        reconstructClassAnnotations();
        reconstructMethodAnnotations();
        reconstructFieldAnnotations();
    }

    private void reconstructClassAnnotations() {
        ClassFileParser.ClassInfo info = classParser.getClassInfo();
        // Class annotations are not currently exposed in ClassInfo
        // This would need to be added to the ClassFileParser
    }

    private void reconstructMethodAnnotations() {
        ClassFileParser.ClassInfo info = classParser.getClassInfo();

        if (info.methods != null) {
            for (var method : info.methods) {
                String methodKey = method.name() + method.descriptor();
                // Annotation reconstruction would go here
                // Currently methods don't expose annotations in the record
            }
        }
    }

    private void reconstructFieldAnnotations() {
        ClassFileParser.ClassInfo info = classParser.getClassInfo();

        if (info.fields != null) {
            for (var field : info.fields) {
                String fieldKey = field.name();
                // Annotation reconstruction would go here
                // Currently fields don't expose annotations in the record
            }
        }
    }

    private List<AnnotationInfo> parseAnnotations(Object annotationData) {
        List<AnnotationInfo> result = new ArrayList<>();
        return result;
    }

    public List<AnnotationInfo> getClassAnnotations(String className) {
        return classAnnotations.getOrDefault(className, List.of());
    }

    public List<AnnotationInfo> getMethodAnnotations(String className, String methodKey) {
        Map<String, List<AnnotationInfo>> methods = methodAnnotations.get(className);
        if (methods == null) return List.of();
        return methods.getOrDefault(methodKey, List.of());
    }

    public List<AnnotationInfo> getFieldAnnotations(String className, String fieldName) {
        Map<String, List<AnnotationInfo>> fields = fieldAnnotations.get(className);
        if (fields == null) return List.of();
        return fields.getOrDefault(fieldName, List.of());
    }

    public record AnnotationInfo(String type, Map<String, Object> values) {
        @Override
        public String toString() {
            if (values.isEmpty()) {
                return "@" + simpleName(type);
            }

            StringBuilder sb = new StringBuilder("@").append(simpleName(type)).append("(");

            boolean first = true;
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (!first) sb.append(", ");
                first = false;

                if (!entry.getKey().equals("value") || values.size() > 1) {
                    sb.append(entry.getKey()).append(" = ");
                }

                sb.append(formatValue(entry.getValue()));
            }

            sb.append(")");
            return sb.toString();
        }

        private String simpleName(String type) {
            int lastDot = type.lastIndexOf('.');
            return lastDot >= 0 ? type.substring(lastDot + 1) : type;
        }

        private String formatValue(Object value) {
            if (value instanceof String s) {
                return "\"" + s + "\"";
            }
            if (value instanceof Class<?> c) {
                return c.getSimpleName() + ".class";
            }
            if (value instanceof Enum<?> e) {
                return e.getClass().getSimpleName() + "." + e.name();
            }
            if (value instanceof Object[] arr) {
                StringBuilder sb = new StringBuilder("{");
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(formatValue(arr[i]));
                }
                sb.append("}");
                return sb.toString();
            }
            return String.valueOf(value);
        }
    }
}
