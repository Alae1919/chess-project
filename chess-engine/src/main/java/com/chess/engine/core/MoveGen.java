package com.chess.engine.core;

import static com.chess.engine.core.Position.*;

/**
 * Move generation into a caller-supplied {@code int[]} (no allocation). Moves are first
 * generated pseudo-legally: obeying how each piece moves, but possibly leaving the mover's own
 * king in check. {@link #legal} filters those by playing each one; the search does the same
 * test as it goes, skipping the work for moves it never looks at.
 */
public final class MoveGen {

    private MoveGen() {}

    /** Large enough for any position: the most legal moves ever seen is 218. */
    public static final int MAX_MOVES = 256;

    private static final long RANK_3 = Bits.RANK_1 << 16;
    private static final long RANK_6 = Bits.RANK_1 << 40;

    /** Writes the legal moves to {@code out} and returns how many. */
    public static int legal(Position pos, int[] out) {
        int n = generate(pos, out, false);
        int us = pos.sideToMove();
        int kept = 0;
        for (int i = 0; i < n; i++) {
            int move = out[i];
            pos.makeMove(move);
            boolean safe = !pos.isAttacked(pos.kingSquare(us), us ^ 1);
            pos.unmakeMove();
            if (safe) out[kept++] = move;
        }
        return kept;
    }

    /** All pseudo-legal moves. */
    public static int pseudoLegal(Position pos, int[] out) {
        return generate(pos, out, false);
    }

    /**
     * Pseudo-legal captures and promotions to a queen: the moves that change the material
     * balance, which is what a quiescence search needs to look at.
     */
    public static int captures(Position pos, int[] out) {
        return generate(pos, out, true);
    }

    private static int generate(Position pos, int[] out, boolean tactical) {
        final int us = pos.sideToMove(), them = us ^ 1;
        final long own = pos.occupancy(us), enemy = pos.occupancy(them), all = pos.occupied();
        final long empty = ~all;
        int n = 0;

        // ---- pawns ---------------------------------------------------------------------------
        final long pawns = pos.bitboard(us, PAWN);
        final long promotionRank = us == WHITE ? Bits.RANK_8 : Bits.RANK_1;
        final int up = us == WHITE ? 8 : -8;

        long single = (us == WHITE ? pawns << 8 : pawns >>> 8) & empty;
        long dbl = (us == WHITE ? ((single & RANK_3) << 8) : ((single & RANK_6) >>> 8)) & empty;

        long promoting = single & promotionRank;
        for (long t = promoting; t != 0; t = Bits.clearLsb(t)) {
            int to = Bits.lsb(t);
            n = addPromotions(out, n, to - up, to, false, tactical);
        }
        if (!tactical) {
            for (long t = single & ~promotionRank; t != 0; t = Bits.clearLsb(t)) {
                int to = Bits.lsb(t);
                out[n++] = Move.make(to - up, to, Move.QUIET);
            }
            for (long t = dbl; t != 0; t = Bits.clearLsb(t)) {
                int to = Bits.lsb(t);
                out[n++] = Move.make(to - 2 * up, to, Move.DOUBLE_PUSH);
            }
        }

        // captures towards the a-file side and the h-file side
        long towardsA = (us == WHITE ? (pawns << 7) : (pawns >>> 9)) & Bits.NOT_FILE_H & enemy;
        long towardsH = (us == WHITE ? (pawns << 9) : (pawns >>> 7)) & Bits.NOT_FILE_A & enemy;
        int stepA = up - 1, stepH = up + 1;
        for (long t = towardsA; t != 0; t = Bits.clearLsb(t)) {
            int to = Bits.lsb(t);
            if ((1L << to & promotionRank) != 0) n = addPromotions(out, n, to - stepA, to, true, tactical);
            else out[n++] = Move.make(to - stepA, to, Move.CAPTURE);
        }
        for (long t = towardsH; t != 0; t = Bits.clearLsb(t)) {
            int to = Bits.lsb(t);
            if ((1L << to & promotionRank) != 0) n = addPromotions(out, n, to - stepH, to, true, tactical);
            else out[n++] = Move.make(to - stepH, to, Move.CAPTURE);
        }

        int ep = pos.epSquare();
        if (ep >= 0) {
            for (long t = Attacks.PAWN[them][ep] & pawns; t != 0; t = Bits.clearLsb(t)) {
                out[n++] = Move.make(Bits.lsb(t), ep, Move.EP_CAPTURE);
            }
        }

        // ---- knights, bishops, rooks, queens, king -------------------------------------------
        final long targets = tactical ? enemy : ~own;

        for (long f = pos.bitboard(us, KNIGHT); f != 0; f = Bits.clearLsb(f)) {
            int from = Bits.lsb(f);
            n = addMoves(out, n, from, Attacks.KNIGHT[from] & targets, enemy);
        }
        for (long f = pos.bitboard(us, BISHOP); f != 0; f = Bits.clearLsb(f)) {
            int from = Bits.lsb(f);
            n = addMoves(out, n, from, Attacks.bishop(from, all) & targets, enemy);
        }
        for (long f = pos.bitboard(us, ROOK); f != 0; f = Bits.clearLsb(f)) {
            int from = Bits.lsb(f);
            n = addMoves(out, n, from, Attacks.rook(from, all) & targets, enemy);
        }
        for (long f = pos.bitboard(us, QUEEN); f != 0; f = Bits.clearLsb(f)) {
            int from = Bits.lsb(f);
            n = addMoves(out, n, from, Attacks.queen(from, all) & targets, enemy);
        }
        int king = pos.kingSquare(us);
        n = addMoves(out, n, king, Attacks.KING[king] & targets, enemy);

        if (!tactical) n = addCastling(pos, out, n, us, them, all);
        return n;
    }

    private static int addMoves(int[] out, int n, int from, long targets, long enemy) {
        for (long t = targets; t != 0; t = Bits.clearLsb(t)) {
            int to = Bits.lsb(t);
            out[n++] = Move.make(from, to, (enemy & (1L << to)) != 0 ? Move.CAPTURE : Move.QUIET);
        }
        return n;
    }

    /** The four promotions of a pawn move, or just the queen one when only tactical moves are wanted. */
    private static int addPromotions(int[] out, int n, int from, int to, boolean capture, boolean tactical) {
        int base = capture ? Move.PROMO_CAPTURE : Move.PROMO_KNIGHT;
        out[n++] = Move.make(from, to, base + 3);                    // queen
        if (!tactical) {
            out[n++] = Move.make(from, to, base + 2);                // rook
            out[n++] = Move.make(from, to, base + 1);                // bishop
            out[n++] = Move.make(from, to, base);                    // knight
        }
        return n;
    }

    private static int addCastling(Position pos, int[] out, int n, int us, int them, long all) {
        int rights = pos.castlingRights();
        if (us == WHITE) {
            if ((rights & WHITE_KING_SIDE) != 0 && pos.pieceAt(4) == piece(WHITE, KING) && pos.pieceAt(7) == piece(WHITE, ROOK)
                    && (all & 0x60L) == 0
                    && !pos.isAttacked(4, them) && !pos.isAttacked(5, them) && !pos.isAttacked(6, them)) {
                out[n++] = Move.make(4, 6, Move.KING_CASTLE);
            }
            if ((rights & WHITE_QUEEN_SIDE) != 0 && pos.pieceAt(4) == piece(WHITE, KING) && pos.pieceAt(0) == piece(WHITE, ROOK)
                    && (all & 0x0EL) == 0
                    && !pos.isAttacked(4, them) && !pos.isAttacked(3, them) && !pos.isAttacked(2, them)) {
                out[n++] = Move.make(4, 2, Move.QUEEN_CASTLE);
            }
        } else {
            if ((rights & BLACK_KING_SIDE) != 0 && pos.pieceAt(60) == piece(BLACK, KING) && pos.pieceAt(63) == piece(BLACK, ROOK)
                    && (all & (0x60L << 56)) == 0
                    && !pos.isAttacked(60, them) && !pos.isAttacked(61, them) && !pos.isAttacked(62, them)) {
                out[n++] = Move.make(60, 62, Move.KING_CASTLE);
            }
            if ((rights & BLACK_QUEEN_SIDE) != 0 && pos.pieceAt(60) == piece(BLACK, KING) && pos.pieceAt(56) == piece(BLACK, ROOK)
                    && (all & (0x0EL << 56)) == 0
                    && !pos.isAttacked(60, them) && !pos.isAttacked(59, them) && !pos.isAttacked(58, them)) {
                out[n++] = Move.make(60, 58, Move.QUEEN_CASTLE);
            }
        }
        return n;
    }
}
