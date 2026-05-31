package com.divinity.util;

import java.io.*;
import java.util.*;
import java.util.jar.*;

public final class JarHandler {

    private JarHandler() {}

    public record ClazzEntry(String name, byte[] data) {
        public byte[] bytecode() {
            return data;
        }
    }

    public static List<ClazzEntry> listClassFiles(File jarFile) throws IOException {
        List<ClazzEntry> entries = new ArrayList<>();
        try (JarInputStream jis = new JarInputStream(new BufferedInputStream(new FileInputStream(jarFile)))) {
            java.util.jar.JarEntry jarEntry;
            while ((jarEntry = jis.getNextJarEntry()) != null) {
                String entryName = jarEntry.getName();
                if (entryName.endsWith(".class") && !entryName.startsWith("META-INF/versions/")) {
                    entries.add(new ClazzEntry(entryName, IoUtil.readAllBytes(jis)));
                }
            }
        }
        return entries;
    }

    public static List<ClazzEntry> findClassFiles(File directory) throws IOException {
        List<ClazzEntry> entries = new ArrayList<>();
        collectClassFiles(directory, directory, entries);
        return entries;
    }

    private static void collectClassFiles(File baseDir, File dir, List<ClazzEntry> entries) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectClassFiles(baseDir, f, entries);
            } else if (f.getName().endsWith(".class")) {
                String relativePath = baseDir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                entries.add(new ClazzEntry(relativePath, IoUtil.readAllBytes(f)));
            }
        }
    }

    public static void writeJar(File outputFile, Map<String, byte[]> classFiles) throws IOException {
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)))) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(new Attributes.Name("Created-By"), "Divinity Decompiler v1.0.0");
            jos.putNextEntry(new java.util.zip.ZipEntry(JarFile.MANIFEST_NAME));
            manifest.write(jos);
            jos.closeEntry();

            for (var entry : classFiles.entrySet()) {
                String name = entry.getKey().replace('\\', '/');
                jos.putNextEntry(new java.util.zip.ZipEntry(name));
                jos.write(entry.getValue());
                jos.closeEntry();
            }
        }
    }

    public static void createSourceJar(File outputFile, Map<String, String> sourceFiles) throws IOException {
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)))) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(new Attributes.Name("Created-By"), "Divinity Decompiler v5.0.0");
            jos.putNextEntry(new java.util.zip.ZipEntry(JarFile.MANIFEST_NAME));
            manifest.write(jos);
            jos.closeEntry();

            for (var entry : sourceFiles.entrySet()) {
                String name = entry.getKey().replace('\\', '/');
                jos.putNextEntry(new java.util.zip.ZipEntry(name));
                jos.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
    }
}
