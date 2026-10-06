package com.chess.engine.core;

/**
 * Which squares each piece attacks, from precomputed tables. Knights, kings and pawns are
 * looked up directly; sliding pieces walk a ray in each direction and stop at the first
 * blocker, found with a single bit scan (nearest blocker = lowest set bit going up the board,
 * highest going down).
 */
public final class Attacks {

    private Attacks() {}

    public static final long[] KNIGHT = new long[64];
    public static final long[] KING = new long[64];
    /** PAWN[color][square]: the squares a pawn of {@code color} on {@code square} attacks. */
    public static final long[][] PAWN = new long[2][64];

    // Directions: N, NE, E, SE, S, SW, W, NW. Up the board (N, NE, E, NW) are the "positive" ones.
    private static final int N = 0, NE = 1, E = 2, SE = 3, S = 4, SW = 5, W = 6, NW = 7;
    private static final int[] FILE_STEP = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] RANK_STEP = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final long[][] RAY = new long[8][64];

    static {
        for (int sq = 0; sq < 64; sq++) {
            int f = Bits.file(sq), r = Bits.rank(sq);
            for (int[] d : new int[][]{{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}}) {
                KNIGHT[sq] |= bitIfOnBoard(f + d[0], r + d[1]);
            }
            for (int df = -1; df <= 1; df++) {
                for (int dr = -1; dr <= 1; dr++) {
                    if (df != 0 || dr != 0) KING[sq] |= bitIfOnBoard(f + df, r + dr);
                }
            }
            PAWN[0][sq] = bitIfOnBoard(f - 1, r + 1) | bitIfOnBoard(f + 1, r + 1);   // white attacks upward
            PAWN[1][sq] = bitIfOnBoard(f - 1, r - 1) | bitIfOnBoard(f + 1, r - 1);   // black attacks downward
            for (int dir = 0; dir < 8; dir++) {
                long ray = 0;
                for (int ff = f + FILE_STEP[dir], rr = r + RANK_STEP[dir];
                     ff >= 0 && ff < 8 && rr >= 0 && rr < 8;
                     ff += FILE_STEP[dir], rr += RANK_STEP[dir]) {
                    ray |= 1L << Bits.square(ff, rr);
                }
                RAY[dir][sq] = ray;
            }
        }
    }

    private static long bitIfOnBoard(int file, int rank) {
        return (file < 0 || file > 7 || rank < 0 || rank > 7) ? 0 : 1L << Bits.square(file, rank);
    }

    /** A ray from {@code sq} cut off at its first blocker (the blocker itself is attacked). */
    private static long cast(int dir, int sq, long occupied, boolean up) {
        long ray = RAY[dir][sq];
        long blockers = ray & occupied;
        if (blockers == 0) return ray;
        int first = up ? Long.numberOfTrailingZeros(blockers) : 63 - Long.numberOfLeadingZeros(blockers);
        return ray ^ RAY[dir][first];
    }

    public static long bishop(int sq, long occupied) {
        return cast(NE, sq, occupied, true) | cast(NW, sq, occupied, true)
             | cast(SE, sq, occupied, false) | cast(SW, sq, occupied, false);
    }

    public static long rook(int sq, long occupied) {
        return cast(N, sq, occupied, true) | cast(E, sq, occupied, true)
             | cast(S, sq, occupied, false) | cast(W, sq, occupied, false);
    }

    public static long queen(int sq, long occupied) {
        return bishop(sq, occupied) | rook(sq, occupied);
    }
}
