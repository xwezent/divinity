package com.divinity.incremental;

import com.divinity.classfile.ClassFileParser;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class IncrementalDecompiler {

    private final Path cacheDir;
    private final Map<String, CacheEntry> cache;
    private final MessageDigest md5;

    public IncrementalDecompiler(Path cacheDir) throws Exception {
        this.cacheDir = cacheDir;
        this.cache = new ConcurrentHashMap<>();
        this.md5 = MessageDigest.getInstance("MD5");

        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }

        loadCache();
    }

    public boolean needsDecompilation(String className, byte[] bytecode) {
        String hash = computeHash(bytecode);
        CacheEntry entry = cache.get(className);

        if (entry == null) return true;
        if (!entry.hash().equals(hash)) return true;

        Path cachedFile = cacheDir.resolve(className.replace('.', '/') + ".java");
        return !Files.exists(cachedFile);
    }

    public void cacheResult(String className, byte[] bytecode, String sourceCode) {
        try {
            String hash = computeHash(bytecode);
            Path outputFile = cacheDir.resolve(className.replace('.', '/') + ".java");

            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, sourceCode);

            CacheEntry entry = new CacheEntry(
                className,
                hash,
                System.currentTimeMillis(),
                outputFile.toString()
            );

            cache.put(className, entry);
            saveCache();
        } catch (IOException e) {
        }
    }

    public String getCachedSource(String className) {
        CacheEntry entry = cache.get(className);
        if (entry == null) return null;

        try {
            Path cachedFile = Paths.get(entry.path());
            if (Files.exists(cachedFile)) {
                return Files.readString(cachedFile);
            }
        } catch (IOException e) {
        }

        return null;
    }

    private String computeHash(byte[] data) {
        synchronized (md5) {
            md5.reset();
            byte[] hash = md5.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }

    private void loadCache() {
        Path cacheFile = cacheDir.resolve("cache.dat");
        if (!Files.exists(cacheFile)) return;

        try (ObjectInputStream ois = new ObjectInputStream(
                Files.newInputStream(cacheFile))) {
            @SuppressWarnings("unchecked")
            Map<String, CacheEntry> loaded = (Map<String, CacheEntry>) ois.readObject();
            cache.putAll(loaded);
        } catch (Exception e) {
        }
    }

    private void saveCache() {
        Path cacheFile = cacheDir.resolve("cache.dat");

        try (ObjectOutputStream oos = new ObjectOutputStream(
                Files.newOutputStream(cacheFile))) {
            oos.writeObject(new HashMap<>(cache));
        } catch (IOException e) {
        }
    }

    public void clearCache() {
        cache.clear();
        try {
            Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                    }
                });
        } catch (IOException e) {
        }
    }

    public CacheStatistics getStatistics() {
        int totalEntries = cache.size();
        long totalSize = 0;
        long oldestEntry = Long.MAX_VALUE;
        long newestEntry = 0;

        for (CacheEntry entry : cache.values()) {
            try {
                Path file = Paths.get(entry.path());
                if (Files.exists(file)) {
                    totalSize += Files.size(file);
                }
            } catch (IOException e) {
            }

            oldestEntry = Math.min(oldestEntry, entry.timestamp());
            newestEntry = Math.max(newestEntry, entry.timestamp());
        }

        return new CacheStatistics(
            totalEntries,
            totalSize,
            oldestEntry,
            newestEntry
        );
    }

    public record CacheEntry(String className, String hash, long timestamp, String path)
        implements Serializable {}

    public record CacheStatistics(int entries, long totalSize, long oldestEntry, long newestEntry) {
        public String formatSize() {
            if (totalSize < 1024) return totalSize + " B";
            if (totalSize < 1024 * 1024) return (totalSize / 1024) + " KB";
            return (totalSize / (1024 * 1024)) + " MB";
        }

        @Override
        public String toString() {
            return String.format("Cache: %d entries, %s total",
                entries, formatSize());
        }
    }
}
