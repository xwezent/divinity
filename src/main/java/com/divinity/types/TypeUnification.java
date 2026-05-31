package com.divinity.types;

import java.util.*;

public final class TypeUnification {

    private final List<TypeConstraint> constraints;
    private final Map<String, TypeVariable> variables;
    private final Map<TypeVariable, JavaType> substitution;

    public TypeUnification(List<TypeConstraint> constraints, Map<String, TypeVariable> variables) {
        this.constraints = constraints;
        this.variables = variables;
        this.substitution = new LinkedHashMap<>();
    }

    public void solve() {
        boolean changed = true;
        int iterations = 0;

        while (changed && iterations < 100) {
            changed = false;
            iterations++;

            for (TypeConstraint constraint : new ArrayList<>(constraints)) {
                if (processConstraint(constraint)) {
                    changed = true;
                }
            }

            if (propagateSubstitutions()) {
                changed = true;
            }
        }

        applySubstitutions();
    }

    private boolean processConstraint(TypeConstraint constraint) {
        return switch (constraint) {
            case TypeConstraint.Equality eq -> unify(eq.left(), eq.right());
            case TypeConstraint.Assignment assign -> assign(assign.target(), assign.source());
            case TypeConstraint.Subtype sub -> checkSubtype(sub.subtype(), sub.supertype());
            case TypeConstraint.Cast cast -> handleCast(cast.source(), cast.targetType(), cast.result());
            case TypeConstraint.NewInstance newInst -> assign(newInst.result(), newInst.type());
            case TypeConstraint.MethodCall call -> handleMethodCall(call);
            case TypeConstraint.FieldAccess field -> handleFieldAccess(field);
            case TypeConstraint.ArrayAccess arr -> handleArrayAccess(arr);
            case TypeConstraint.ArrayStore store -> handleArrayStore(store);
            default -> false;
        };
    }

    private boolean unify(TypeVariable left, TypeVariable right) {
        JavaType leftType = getType(left);
        JavaType rightType = getType(right);

        if (leftType instanceof JavaType.Unknown && !(rightType instanceof JavaType.Unknown)) {
            substitution.put(left, rightType);
            left.setResolvedType(rightType);
            return true;
        }

        if (rightType instanceof JavaType.Unknown && !(leftType instanceof JavaType.Unknown)) {
            substitution.put(right, leftType);
            right.setResolvedType(leftType);
            return true;
        }

        if (leftType.equals(rightType)) {
            return false;
        }

        JavaType unified = unifyTypes(leftType, rightType);
        if (unified != null && !unified.equals(leftType)) {
            substitution.put(left, unified);
            left.setResolvedType(unified);
            return true;
        }
        if (unified != null && !unified.equals(rightType)) {
            substitution.put(right, unified);
            right.setResolvedType(unified);
            return true;
        }

        return false;
    }

    private boolean assign(TypeVariable target, JavaType source) {
        JavaType currentType = getType(target);

        if (currentType instanceof JavaType.Unknown) {
            substitution.put(target, source);
            target.setResolvedType(source);
            return true;
        }

        if (currentType.equals(source)) {
            return false;
        }

        JavaType unified = unifyTypes(currentType, source);
        if (unified != null && !unified.equals(currentType)) {
            substitution.put(target, unified);
            target.setResolvedType(unified);
            return true;
        }

        return false;
    }

    private boolean checkSubtype(TypeVariable subtype, TypeVariable supertype) {
        JavaType subType = getType(subtype);
        JavaType superType = getType(supertype);

        if (subType instanceof JavaType.Unknown || superType instanceof JavaType.Unknown) {
            return false;
        }

        if (!subType.isSubtypeOf(superType)) {
            JavaType common = subType.commonSupertype(superType);
            if (!common.equals(superType)) {
                substitution.put(supertype, common);
                supertype.setResolvedType(common);
                return true;
            }
        }

        return false;
    }

    private boolean handleCast(TypeVariable source, JavaType targetType, TypeVariable result) {
        substitution.put(result, targetType);
        result.setResolvedType(targetType);
        return true;
    }

    private boolean handleMethodCall(TypeConstraint.MethodCall call) {
        JavaType receiverType = getType(call.receiver());

        if (receiverType instanceof JavaType.Class classType) {
            JavaType returnType = inferMethodReturnType(
                classType, call.methodName(), call.descriptor());
            if (returnType != null) {
                return assign(call.returnType(), returnType);
            }
        }

        return false;
    }

    private boolean handleFieldAccess(TypeConstraint.FieldAccess field) {
        JavaType receiverType = getType(field.receiver());

        if (receiverType instanceof JavaType.Class classType) {
            JavaType fieldType = inferFieldType(classType, field.fieldName(), field.descriptor());
            if (fieldType != null) {
                return assign(field.fieldType(), fieldType);
            }
        }

        return false;
    }

    private boolean handleArrayAccess(TypeConstraint.ArrayAccess arr) {
        JavaType arrayType = getType(arr.array());

        if (arrayType instanceof JavaType.Array arrayT) {
            return assign(arr.element(), arrayT.elementType());
        }

        return false;
    }

    private boolean handleArrayStore(TypeConstraint.ArrayStore store) {
        JavaType arrayType = getType(store.array());

        if (arrayType instanceof JavaType.Array arrayT) {
            JavaType valueType = getType(store.value());
            if (!valueType.isSubtypeOf(arrayT.elementType())) {
                return false;
            }
        }

        return false;
    }

    private JavaType getType(TypeVariable var) {
        JavaType type = substitution.get(var);
        if (type != null) return type;
        return var.resolvedType();
    }

    private JavaType unifyTypes(JavaType t1, JavaType t2) {
        if (t1.equals(t2)) return t1;
        if (t1 instanceof JavaType.Unknown) return t2;
        if (t2 instanceof JavaType.Unknown) return t1;
        if (t1 instanceof JavaType.Null) return t2;
        if (t2 instanceof JavaType.Null) return t1;

        if (t1 instanceof JavaType.Class c1 && t2 instanceof JavaType.Class c2) {
            if (c1.name().equals(c2.name())) {
                if (c1.typeArguments().isEmpty() && !c2.typeArguments().isEmpty()) {
                    return c2;
                }
                if (!c1.typeArguments().isEmpty() && c2.typeArguments().isEmpty()) {
                    return c1;
                }
                if (c1.typeArguments().size() == c2.typeArguments().size()) {
                    List<JavaType> unifiedArgs = new ArrayList<>();
                    for (int i = 0; i < c1.typeArguments().size(); i++) {
                        JavaType arg1 = c1.typeArguments().get(i);
                        JavaType arg2 = c2.typeArguments().get(i);
                        JavaType unified = unifyTypes(arg1, arg2);
                        if (unified == null) return null;
                        unifiedArgs.add(unified);
                    }
                    return new JavaType.Class(c1.name(), unifiedArgs);
                }
                return c1;
            }
            return t1.commonSupertype(t2);
        }

        if (t1 instanceof JavaType.Array a1 && t2 instanceof JavaType.Array a2) {
            JavaType elementType = unifyTypes(a1.elementType(), a2.elementType());
            if (elementType != null) {
                return new JavaType.Array(elementType);
            }
        }

        return t1.commonSupertype(t2);
    }

    private boolean propagateSubstitutions() {
        boolean changed = false;

        for (TypeVariable var : variables.values()) {
            JavaType type = substitution.get(var);
            if (type != null && !type.equals(var.resolvedType())) {
                var.setResolvedType(type);
                changed = true;
            }
        }

        return changed;
    }

    private void applySubstitutions() {
        for (TypeVariable var : variables.values()) {
            JavaType type = substitution.get(var);
            if (type != null) {
                var.setResolvedType(type);
            }
        }
    }

    private JavaType inferMethodReturnType(JavaType.Class receiverType, String methodName, String descriptor) {
        if (descriptor == null) return new JavaType.Unknown();
        int parenIdx = descriptor.indexOf(')');
        if (parenIdx < 0 || parenIdx + 1 >= descriptor.length()) return JavaType.Primitive.VOID;
        String returnDesc = descriptor.substring(parenIdx + 1);
        return JavaType.fromDescriptor(returnDesc);
    }

    private JavaType inferFieldType(JavaType.Class receiverType, String fieldName, String descriptor) {
        if (descriptor == null) return new JavaType.Unknown();
        return JavaType.fromDescriptor(descriptor);
    }
}
