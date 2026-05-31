package com.divinity.writer;

import java.util.*;

class ImportCollector {

    private final Set<String> imports;

    ImportCollector() {
        this.imports = new LinkedHashSet<>();
    }

    void add(String fullName) {
        if (fullName == null) return;
        String name = fullName.replace('/', '.');
        if (name.startsWith("java.lang.") && !name.contains("$")) {
            String simple = name.substring(10);
            if (!simple.contains(".")) return;
        }
        if (name.contains(".") && !name.equals("I") && !name.equals("J")
                && !name.equals("F") && !name.equals("D") && !name.equals("Z")
                && !name.equals("B") && !name.equals("C") && !name.equals("S")
                && !name.equals("V") && !name.endsWith("[]")) {
            imports.add(name);
        }
    }

    void addDescriptors(String descriptor) {
        if (descriptor == null) return;
        for (String part : extractTypes(descriptor)) {
            add(part);
        }
    }

    private List<String> extractTypes(String descriptor) {
        List<String> types = new ArrayList<>();
        if (descriptor == null) return types;

        int parenEnd = descriptor.indexOf(')');
        String full = parenEnd >= 0 ? descriptor.substring(1, parenEnd) + descriptor.substring(parenEnd + 1) : descriptor;

        int i = 0;
        while (i < full.length()) {
            while (i < full.length() && full.charAt(i) == '[') i++;
            if (i >= full.length()) break;
            if (full.charAt(i) == 'L') {
                int end = full.indexOf(';', i);
                if (end < 0) end = full.length();
                types.add(full.substring(i + 1, end));
                i = end + 1;
            } else {
                i++;
            }
        }
        return types;
    }

    List<String> getImports(String packageName) {
        List<String> result = new ArrayList<>();
        for (String imp : imports) {
            if (packageName != null && imp.startsWith(packageName + ".")) {
                String relative = imp.substring(packageName.length() + 1);
                if (!relative.contains(".")) continue;
            }
            result.add(imp);
        }
        Collections.sort(result);
        return result;
    }
}
