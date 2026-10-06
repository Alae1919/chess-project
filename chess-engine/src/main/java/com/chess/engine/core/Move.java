package com.chess.engine.core;

/**
 * A move packed in an int: bits 0-5 the from square, 6-11 the to square, 12-15 a flag.
 * The flag values are the usual ones: bit 2 marks a capture, bit 3 a promotion, and for
 * a promotion the low two bits say which piece (knight, bishop, rook, queen).
 * Zero is "no move" (a1 to a1 can't happen).
 */
public final class Move {

    private Move() {}

    public static final int NONE = 0;

    public static final int QUIET = 0;
    public static final int DOUBLE_PUSH = 1;
    public static final int KING_CASTLE = 2;
    public static final int QUEEN_CASTLE = 3;
    public static final int CAPTURE = 4;
    public static final int EP_CAPTURE = 5;
    public static final int PROMO_KNIGHT = 8;     // 8..11 quiet promotions: knight, bishop, rook, queen
    public static final int PROMO_CAPTURE = 12;   // 12..15 the same with a capture

    public static int make(int from, int to, int flag) { return from | (to << 6) | (flag << 12); }

    public static int from(int move) { return move & 63; }
    public static int to(int move) { return (move >>> 6) & 63; }
    public static int flag(int move) { return (move >>> 12) & 15; }

    public static boolean isCapture(int move) { return (flag(move) & 4) != 0; }
    public static boolean isPromotion(int move) { return (flag(move) & 8) != 0; }
    public static boolean isEnPassant(int move) { return flag(move) == EP_CAPTURE; }
    public static boolean isCastle(int move) { int f = flag(move); return f == KING_CASTLE || f == QUEEN_CASTLE; }

    /** A move that changes no material: not a capture and not a promotion. */
    public static boolean isQuiet(int move) { return (flag(move) & 12) == 0; }

    /** The promotion piece type (knight 1 .. queen 4); only meaningful for a promotion. */
    public static int promotionType(int move) { return (flag(move) & 3) + 1; }

    /** UCI text such as "e2e4" or "e7e8q". */
    public static String toUci(int move) {
        if (move == NONE) return "0000";
        String s = Bits.name(from(move)) + Bits.name(to(move));
        return isPromotion(move) ? s + "nbrq".charAt(flag(move) & 3) : s;
    }
}
