package com.divinity.util;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class IoUtil {

    private IoUtil() {}

    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) != -1) {
            buf.write(tmp, 0, n);
        }
        return buf.toByteArray();
    }

    public static byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return readAllBytes(fis);
        }
    }

    public static void writeAllBytes(File file, byte[] data) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }

    public static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            w.write(text);
        }
    }

    public static int readU1(ByteBuffer buf) {
        return buf.get() & 0xFF;
    }

    public static int readU2(ByteBuffer buf) {
        return buf.getShort() & 0xFFFF;
    }

    public static int readS2(ByteBuffer buf) {
        return buf.getShort();
    }

    public static int readU4(ByteBuffer buf) {
        return buf.getInt();
    }

    public static long readU8(ByteBuffer buf) {
        return buf.getLong();
    }

    public static int readS4(ByteBuffer buf) {
        return buf.getInt();
    }

    public static float readFloat(ByteBuffer buf) {
        return buf.getFloat();
    }

    public static double readDouble(ByteBuffer buf) {
        return buf.getDouble();
    }

    public static ByteBuffer buffer(byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
    }
}
