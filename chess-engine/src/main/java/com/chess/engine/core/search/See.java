package com.chess.engine.core.search;

import com.chess.engine.core.Attacks;
import com.chess.engine.core.Move;
import com.chess.engine.core.Position;

import static com.chess.engine.core.Position.*;

/**
 * Static exchange evaluation: if both sides keep capturing on one square, always with their
 * cheapest piece and stopping when it no longer pays, who comes out ahead? Used to decide
 * which captures are worth searching first, and which are not worth searching at all.
 */
public final class See {

    private See() {}

    public static final int[] VALUE = {100, 320, 330, 500, 900, 20_000};

    /** True if the exchange started by {@code move} gains at least {@code threshold} centipawns. */
    public static boolean atLeast(Position pos, int move, int threshold) {
        if (Move.isCastle(move)) return threshold <= 0;

        final int from = Move.from(move), to = Move.to(move);
        final int moving = pos.pieceAt(from);
        int victim = Move.isEnPassant(move) ? VALUE[PAWN]
                   : pos.pieceAt(to) == NO_PIECE ? 0 : VALUE[typeOf(pos.pieceAt(to))];
        if (Move.isPromotion(move)) victim += VALUE[Move.promotionType(move)] - VALUE[PAWN];

        int swap = victim - threshold;
        if (swap < 0) return false;
        swap = VALUE[typeOf(moving)] - swap;
        if (swap <= 0) return true;

        long occ = pos.occupied() ^ (1L << from) ^ (1L << to);
        if (Move.isEnPassant(move)) occ ^= 1L << (colorOf(moving) == WHITE ? to - 8 : to + 8);

        long bishopsQueens = pos.bitboard(piece(WHITE, BISHOP)) | pos.bitboard(piece(BLACK, BISHOP))
                           | pos.bitboard(piece(WHITE, QUEEN)) | pos.bitboard(piece(BLACK, QUEEN));
        long rooksQueens = pos.bitboard(piece(WHITE, ROOK)) | pos.bitboard(piece(BLACK, ROOK))
                         | pos.bitboard(piece(WHITE, QUEEN)) | pos.bitboard(piece(BLACK, QUEEN));

        int stm = colorOf(moving);
        long attackers = pos.attackersTo(to, occ);
        int result = 1;

        while (true) {
            stm ^= 1;
            attackers &= occ;
            long mine = attackers & pos.occupancy(stm);
            if (mine == 0) break;
            result ^= 1;

            long least;
            if ((least = mine & pos.bitboard(stm, PAWN)) != 0) {
                if ((swap = VALUE[PAWN] - swap) < result) break;
                occ ^= Long.lowestOneBit(least);
                attackers |= Attacks.bishop(to, occ) & bishopsQueens;
            } else if ((least = mine & pos.bitboard(stm, KNIGHT)) != 0) {
                if ((swap = VALUE[KNIGHT] - swap) < result) break;
                occ ^= Long.lowestOneBit(least);
            } else if ((least = mine & pos.bitboard(stm, BISHOP)) != 0) {
                if ((swap = VALUE[BISHOP] - swap) < result) break;
                occ ^= Long.lowestOneBit(least);
                attackers |= Attacks.bishop(to, occ) & bishopsQueens;
            } else if ((least = mine & pos.bitboard(stm, ROOK)) != 0) {
                if ((swap = VALUE[ROOK] - swap) < result) break;
                occ ^= Long.lowestOneBit(least);
                attackers |= Attacks.rook(to, occ) & rooksQueens;
            } else if ((least = mine & pos.bitboard(stm, QUEEN)) != 0) {
                if ((swap = VALUE[QUEEN] - swap) < result) break;
                occ ^= Long.lowestOneBit(least);
                attackers |= (Attacks.bishop(to, occ) & bishopsQueens) | (Attacks.rook(to, occ) & rooksQueens);
            } else {
                // the king takes last, and only if the other side has nothing left to retake with
                return ((attackers & ~pos.occupancy(stm)) != 0 ? result ^ 1 : result) != 0;
            }
        }
        return result != 0;
    }
}
