package com.divinity.deobf.mba;

import java.util.*;

public final class MbaPattern {

    private static final List<Pattern> PATTERNS = new ArrayList<>();

    static {
        initPatterns();
    }

    public static com.divinity.deobf.mba.MbaSolver.Node tryMatch(com.divinity.deobf.mba.MbaSolver.Node node) {
        for (Pattern pattern : PATTERNS) {
            com.divinity.deobf.mba.MbaSolver.Node result = pattern.tryMatch(node);
            if (result != null) return result;
        }
        return null;
    }

    private static void initPatterns() {
        PATTERNS.add(new Pattern(
            "(x ^ y) + 2 * (x & y)",
            "x + y"
        ));

        PATTERNS.add(new Pattern(
            "(x | y) + (x & y)",
            "x + y"
        ));

        PATTERNS.add(new Pattern(
            "(x & y) + (x | y)",
            "x + y"
        ));

        PATTERNS.add(new Pattern(
            "2 * (x | y) - (x ^ y)",
            "x + y"
        ));

        PATTERNS.add(new Pattern(
            "(x ^ y) - 2 * (x & ~y)",
            "x + y"
        ));

        PATTERNS.add(new Pattern(
            "(x | y) - (x & ~y)",
            "x"
        ));

        PATTERNS.add(new Pattern(
            "(x & y) | (x & ~y)",
            "x"
        ));

        PATTERNS.add(new Pattern(
            "(x ^ ~y) + 2 * (x | y) + 1",
            "x - y"
        ));

        PATTERNS.add(new Pattern(
            "(x | y) - (y & ~x)",
            "x | y"
        ));

        PATTERNS.add(new Pattern(
            "(x & y) + (x | y)",
            "x + y"
        ));

        PATTERNS.add(new Pattern(
            "~(~x & ~y)",
            "x | y"
        ));

        PATTERNS.add(new Pattern(
            "~(~x | ~y)",
            "x & y"
        ));

        PATTERNS.add(new Pattern(
            "(x & ~y) | (~x & y)",
            "x ^ y"
        ));

        PATTERNS.add(new Pattern(
            "~x & y | x & ~y",
            "x ^ y"
        ));

        PATTERNS.add(new Pattern(
            "(x | y) & ~(x & y)",
            "x ^ y"
        ));

        PATTERNS.add(new Pattern(
            "x + y - 2 * (x & y)",
            "x ^ y"
        ));

        PATTERNS.add(new Pattern(
            "(x ^ y) & ~(x & y)",
            "x ^ y"
        ));

        PATTERNS.add(new Pattern(
            "~(x ^ y)",
            "~(x ^ y)"
        ));

        PATTERNS.add(new Pattern(
            "x * y + x + y",
            "(x + 1) * (y + 1) - 1"
        ));

        PATTERNS.add(new Pattern(
            "(x & -x)",
            "lowest_set_bit(x)"
        ));

        PATTERNS.add(new Pattern(
            "x & (x - 1)",
            "clear_lowest_set_bit(x)"
        ));

        PATTERNS.add(new Pattern(
            "x | (x + 1)",
            "set_lowest_clear_bit(x)"
        ));

        PATTERNS.add(new Pattern(
            "~x & (x + 1)",
            "isolate_lowest_clear_bit(x)"
        ));

        PATTERNS.add(new Pattern(
            "(x ^ (x - 1))",
            "mask_trailing_zeros(x)"
        ));
    }

    private static class Pattern {
        private final String pattern;
        private final String replacement;

        Pattern(String pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }

        com.divinity.deobf.mba.MbaSolver.Node tryMatch(com.divinity.deobf.mba.MbaSolver.Node node) {
            return null;
        }
    }

    public static List<KnownMba> getKnownMbas() {
        List<KnownMba> mbas = new ArrayList<>();

        mbas.add(new KnownMba(
            "(x ^ y) + 2 * (x & y)",
            "x + y",
            "Addition via XOR and AND"
        ));

        mbas.add(new KnownMba(
            "(x | y) + (x & y)",
            "x + y",
            "Addition via OR and AND"
        ));

        mbas.add(new KnownMba(
            "2 * (x | y) - (x ^ y)",
            "x + y",
            "Addition via OR and XOR"
        ));

        mbas.add(new KnownMba(
            "(x ^ y) - 2 * (x & ~y)",
            "x + y",
            "Addition with negation"
        ));

        mbas.add(new KnownMba(
            "(x | y) - (x & ~y)",
            "x",
            "Identity via OR and AND"
        ));

        mbas.add(new KnownMba(
            "(x & y) | (x & ~y)",
            "x",
            "Identity via AND and OR"
        ));

        mbas.add(new KnownMba(
            "(x ^ ~y) + 2 * (x | y) + 1",
            "x - y",
            "Subtraction via XOR and OR"
        ));

        mbas.add(new KnownMba(
            "~(~x & ~y)",
            "x | y",
            "De Morgan's law"
        ));

        mbas.add(new KnownMba(
            "~(~x | ~y)",
            "x & y",
            "De Morgan's law"
        ));

        mbas.add(new KnownMba(
            "(x & ~y) | (~x & y)",
            "x ^ y",
            "XOR via AND and OR"
        ));

        mbas.add(new KnownMba(
            "(x | y) & ~(x & y)",
            "x ^ y",
            "XOR via OR and AND"
        ));

        mbas.add(new KnownMba(
            "x + y - 2 * (x & y)",
            "x ^ y",
            "XOR via addition"
        ));

        mbas.add(new KnownMba(
            "x * 2",
            "x << 1",
            "Multiplication by 2"
        ));

        mbas.add(new KnownMba(
            "x / 2",
            "x >> 1",
            "Division by 2"
        ));

        mbas.add(new KnownMba(
            "x % 2",
            "x & 1",
            "Modulo 2"
        ));

        mbas.add(new KnownMba(
            "-x",
            "~x + 1",
            "Two's complement negation"
        ));

        mbas.add(new KnownMba(
            "~x + 1",
            "-x",
            "Two's complement negation"
        ));

        mbas.add(new KnownMba(
            "x & (x - 1)",
            "clear_lowest_bit(x)",
            "Clear lowest set bit"
        ));

        mbas.add(new KnownMba(
            "x | (x - 1)",
            "set_trailing_bits(x)",
            "Set all trailing bits"
        ));

        mbas.add(new KnownMba(
            "x ^ (x - 1)",
            "mask_trailing_bits(x)",
            "Mask trailing bits"
        ));

        mbas.add(new KnownMba(
            "x & -x",
            "isolate_lowest_bit(x)",
            "Isolate lowest set bit"
        ));

        return mbas;
    }

    public record KnownMba(String obfuscated, String simplified, String description) {}
}
