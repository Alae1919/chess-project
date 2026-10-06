package com.chess.engine.core;

/**
 * Squares and bitboards. A square is 0..63 with a1 = 0, b1 = 1, ... h1 = 7, a2 = 8, ... h8 = 63,
 * so {@code square = 8 * rank + file} (rank 0 is the first rank), and bit {@code s} of a
 * bitboard is square {@code s}.
 */
public final class Bits {

    private Bits() {}

    public static final long FILE_A = 0x0101010101010101L;
    public static final long FILE_H = FILE_A << 7;
    public static final long RANK_1 = 0xFFL;
    public static final long RANK_2 = RANK_1 << 8;
    public static final long RANK_4 = RANK_1 << 24;
    public static final long RANK_5 = RANK_1 << 32;
    public static final long RANK_7 = RANK_1 << 48;
    public static final long RANK_8 = RANK_1 << 56;
    public static final long NOT_FILE_A = ~FILE_A;
    public static final long NOT_FILE_H = ~FILE_H;

    public static int file(int square) { return square & 7; }
    public static int rank(int square) { return square >>> 3; }
    public static int square(int file, int rank) { return rank * 8 + file; }

    public static long bit(int square) { return 1L << square; }

    /** The lowest square set in {@code b}; undefined for an empty board. */
    public static int lsb(long b) { return Long.numberOfTrailingZeros(b); }

    /** The highest square set in {@code b}; undefined for an empty board. */
    public static int msb(long b) { return 63 - Long.numberOfLeadingZeros(b); }

    public static int count(long b) { return Long.bitCount(b); }

    /** {@code b} without its lowest set bit. */
    public static long clearLsb(long b) { return b & (b - 1); }

    public static boolean isLightSquare(int square) { return ((file(square) + rank(square)) & 1) == 1; }

    /** "e4" for square 28. */
    public static String name(int square) {
        return "" + (char) ('a' + file(square)) + (char) ('1' + rank(square));
    }

    /** The square named by "e4", or -1 if the text isn't one. */
    public static int parse(String name) {
        if (name == null || name.length() != 2) return -1;
        int f = name.charAt(0) - 'a', r = name.charAt(1) - '1';
        return (f < 0 || f > 7 || r < 0 || r > 7) ? -1 : square(f, r);
    }
}
