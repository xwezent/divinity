package com.divinity.types;

import java.util.*;

public final class TypeVariable {

    private final String name;
    private final int id;
    private JavaType resolvedType;
    private final Set<TypeConstraint> constraints;

    public TypeVariable(String name, int id) {
        this.name = name;
        this.id = id;
        this.resolvedType = new JavaType.Unknown();
        this.constraints = new LinkedHashSet<>();
    }

    public String name() { return name; }
    public int id() { return id; }
    public JavaType resolvedType() { return resolvedType; }
    public Set<TypeConstraint> constraints() { return constraints; }

    public void setResolvedType(JavaType type) {
        this.resolvedType = type;
    }

    public void addConstraint(TypeConstraint constraint) {
        constraints.add(constraint);
    }

    @Override
    public String toString() {
        return name + "_" + id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeVariable tv)) return false;
        return id == tv.id && name.equals(tv.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, id);
    }
}
