package com.divinity.types;

import java.util.*;

public sealed interface TypeConstraint {

    record Equality(TypeVariable left, TypeVariable right) implements TypeConstraint {
        @Override
        public String toString() {
            return left + " = " + right;
        }
    }

    record Subtype(TypeVariable subtype, TypeVariable supertype) implements TypeConstraint {
        @Override
        public String toString() {
            return subtype + " <: " + supertype;
        }
    }

    record Assignment(TypeVariable target, JavaType source) implements TypeConstraint {
        @Override
        public String toString() {
            return target + " := " + source;
        }
    }

    record MethodCall(TypeVariable receiver, String methodName, String descriptor,
                      List<TypeVariable> arguments, TypeVariable returnType) implements TypeConstraint {
        @Override
        public String toString() {
            return receiver + "." + methodName + "(" +
                   String.join(", ", arguments.stream().map(TypeVariable::toString).toList()) +
                   ") : " + returnType;
        }
    }

    record FieldAccess(TypeVariable receiver, String fieldName, String descriptor,
                       TypeVariable fieldType) implements TypeConstraint {
        @Override
        public String toString() {
            return receiver + "." + fieldName + " : " + fieldType;
        }
    }

    record Cast(TypeVariable source, JavaType targetType, TypeVariable result) implements TypeConstraint {
        @Override
        public String toString() {
            return result + " = (" + targetType + ")" + source;
        }
    }

    record InstanceOf(TypeVariable source, JavaType targetType) implements TypeConstraint {
        @Override
        public String toString() {
            return source + " instanceof " + targetType;
        }
    }

    record ArrayAccess(TypeVariable array, TypeVariable index, TypeVariable element) implements TypeConstraint {
        @Override
        public String toString() {
            return element + " = " + array + "[" + index + "]";
        }
    }

    record ArrayStore(TypeVariable array, TypeVariable index, TypeVariable value) implements TypeConstraint {
        @Override
        public String toString() {
            return array + "[" + index + "] = " + value;
        }
    }

    record NewInstance(TypeVariable result, JavaType type) implements TypeConstraint {
        @Override
        public String toString() {
            return result + " = new " + type;
        }
    }

    record Return(TypeVariable value, JavaType expectedType) implements TypeConstraint {
        @Override
        public String toString() {
            return "return " + value + " : " + expectedType;
        }
    }
}
