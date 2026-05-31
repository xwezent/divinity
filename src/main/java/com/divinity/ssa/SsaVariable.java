package com.divinity.ssa;

import java.util.*;

public final class SsaVariable {

    private final String baseName;
    private final int version;
    private final String descriptor;
    private final Set<SsaInstruction> uses;
    private SsaInstruction definition;

    public SsaVariable(String baseName, int version, String descriptor) {
        this.baseName = baseName;
        this.version = version;
        this.descriptor = descriptor;
        this.uses = new LinkedHashSet<>();
    }

    public String baseName() { return baseName; }
    public int version() { return version; }
    public String descriptor() { return descriptor; }
    public Set<SsaInstruction> uses() { return uses; }
    public SsaInstruction definition() { return definition; }

    public void setDefinition(SsaInstruction def) {
        this.definition = def;
    }

    public void addUse(SsaInstruction use) {
        uses.add(use);
    }

    public void removeUse(SsaInstruction use) {
        uses.remove(use);
    }

    public String name() {
        return baseName + "_" + version;
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SsaVariable v)) return false;
        return version == v.version && baseName.equals(v.baseName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseName, version);
    }
}
