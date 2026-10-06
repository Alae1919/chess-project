package com.chess.engine.core.eval;

import com.chess.engine.core.Attacks;
import com.chess.engine.core.Bits;
import com.chess.engine.core.Position;

import static com.chess.engine.core.Position.*;

/**
 * A hand-written evaluation: material, piece-square tables blended between a midgame and an
 * endgame table by how much material is left, pawn structure, mobility, a few rook and bishop
 * bonuses, a pawn shield for the king, and a mop-up term for winning endgames. It is the
 * baseline the neural evaluation is measured against, and the fallback when no network is
 * available.
 *
 * Every term is added up from White's point of view and the sign is flipped at the end for
 * Black to move, so nothing is counted for one side only.
 */
public final class ClassicalEvaluator implements Evaluator {

    private static final int[] VALUE = {100, 320, 335, 510, 975, 0};
    /** How much each piece type counts towards the game phase; all pieces on is 24. */
    private static final int[] PHASE = {0, 1, 1, 2, 4, 0};
    private static final int FULL_PHASE = 24;

    private static final int BISHOP_PAIR_MG = 30, BISHOP_PAIR_EG = 50;
    private static final int TEMPO_MG = 12, TEMPO_EG = 8;
    private static final int[] PASSED_MG = {0, 5, 10, 20, 35, 60, 100, 0};
    private static final int[] PASSED_EG = {0, 10, 20, 40, 70, 120, 190, 0};
    private static final int DOUBLED_MG = 10, DOUBLED_EG = 20;
    private static final int ISOLATED_MG = 10, ISOLATED_EG = 15;
    private static final int SHIELD_PAWN_MG = 8, OPEN_KING_FILE_MG = 15;

    // mobility: weight per square above (or below) a typical count, midgame and endgame
    private static final int[] MOBILITY_CENTER = {0, 4, 6, 6, 12, 0};
    private static final int[] MOBILITY_MG = {0, 4, 5, 2, 1, 0};
    private static final int[] MOBILITY_EG = {0, 4, 5, 4, 2, 0};

    // ---- precomputed masks ---------------------------------------------------------------------
    private static final long[] FILE = new long[8];
    private static final long[] ADJACENT = new long[8];
    /** PASSED[color][square]: squares ahead of a pawn, on its file and the two next to it. */
    private static final long[][] PASSED = new long[2][64];

    static {
        for (int f = 0; f < 8; f++) {
            FILE[f] = Bits.FILE_A << f;
            ADJACENT[f] = (f > 0 ? Bits.FILE_A << (f - 1) : 0) | (f < 7 ? Bits.FILE_A << (f + 1) : 0);
        }
        for (int sq = 0; sq < 64; sq++) {
            int f = Bits.file(sq), r = Bits.rank(sq);
            long files = FILE[f] | ADJACENT[f];
            long above = r == 7 ? 0 : ~0L << ((r + 1) * 8);
            long below = r == 0 ? 0 : ~0L >>> ((8 - r) * 8);
            PASSED[WHITE][sq] = files & above;
            PASSED[BLACK][sq] = files & below;
        }
    }

    @Override public void reset(Position pos) { }
    @Override public void onMake(Position pos, int move) { }
    @Override public void onUnmake() { }

    @Override
    public int evaluate(Position pos) {
        if (pos.isInsufficientMaterial()) return 0;

        int mg = 0, eg = 0, phase = 0;
        long[] pawns = {pos.bitboard(WHITE, PAWN), pos.bitboard(BLACK, PAWN)};
        long[] pawnAttacks = {pawnAttacks(WHITE, pawns[WHITE]), pawnAttacks(BLACK, pawns[BLACK])};
        long all = pos.occupied();

        for (int color = 0; color < 2; color++) {
            int sign = color == WHITE ? 1 : -1;
            int them = color ^ 1;
            long safe = ~pos.occupancy(color) & ~pawnAttacks[them];   // squares a piece can use without losing to a pawn
            int sMg = 0, sEg = 0;

            for (int type = PAWN; type <= KING; type++) {
                for (long b = pos.bitboard(color, type); b != 0; b = Bits.clearLsb(b)) {
                    int sq = Bits.lsb(b);
                    sMg += VALUE[type] + PieceSquareTables.mg(type, color, sq);
                    sEg += VALUE[type] + PieceSquareTables.eg(type, color, sq);
                    phase += PHASE[type];

                    if (type >= KNIGHT && type <= QUEEN) {
                        long attacks = switch (type) {
                            case KNIGHT -> Attacks.KNIGHT[sq];
                            case BISHOP -> Attacks.bishop(sq, all);
                            case ROOK -> Attacks.rook(sq, all);
                            default -> Attacks.queen(sq, all);
                        };
                        int mobility = Bits.count(attacks & safe) - MOBILITY_CENTER[type];
                        sMg += mobility * MOBILITY_MG[type];
                        sEg += mobility * MOBILITY_EG[type];
                    }
                    if (type == ROOK) {
                        long file = FILE[Bits.file(sq)];
                        if ((file & pawns[color]) == 0) {
                            boolean fullyOpen = (file & pawns[them]) == 0;
                            sMg += fullyOpen ? 20 : 10;
                            sEg += fullyOpen ? 15 : 10;
                        }
                    }
                    if (type == PAWN) {
                        int relRank = color == WHITE ? Bits.rank(sq) : 7 - Bits.rank(sq);
                        if ((PASSED[color][sq] & pawns[them]) == 0) {
                            sMg += PASSED_MG[relRank];
                            sEg += PASSED_EG[relRank];
                        }
                        if ((ADJACENT[Bits.file(sq)] & pawns[color]) == 0) {
                            sMg -= ISOLATED_MG;
                            sEg -= ISOLATED_EG;
                        }
                    }
                }
            }

            // doubled pawns: every pawn after the first on a file
            for (int f = 0; f < 8; f++) {
                int onFile = Bits.count(FILE[f] & pawns[color]);
                if (onFile > 1) {
                    sMg -= DOUBLED_MG * (onFile - 1);
                    sEg -= DOUBLED_EG * (onFile - 1);
                }
            }
            if (Bits.count(pos.bitboard(color, BISHOP)) >= 2) {
                sMg += BISHOP_PAIR_MG;
                sEg += BISHOP_PAIR_EG;
            }
            sMg += kingShelter(pos, color, pawns[color]);

            mg += sign * sMg;
            eg += sign * sEg;
        }

        eg += mopUp(pos);

        int p = Math.min(phase, FULL_PHASE);
        int score = (mg * p + eg * (FULL_PHASE - p)) / FULL_PHASE;
        boolean whiteToMove = pos.sideToMove() == WHITE;
        int tempo = (TEMPO_MG * p + TEMPO_EG * (FULL_PHASE - p)) / FULL_PHASE;
        return whiteToMove ? score + tempo : -score + tempo;
    }

    private static long pawnAttacks(int color, long pawns) {
        return color == WHITE
            ? ((pawns << 7) & Bits.NOT_FILE_H) | ((pawns << 9) & Bits.NOT_FILE_A)
            : ((pawns >>> 9) & Bits.NOT_FILE_H) | ((pawns >>> 7) & Bits.NOT_FILE_A);
    }

    /** Pawns in front of a king that has stayed on its back two ranks, and an open king file. */
    private static int kingShelter(Position pos, int color, long ownPawns) {
        int king = pos.kingSquare(color);
        int rank = Bits.rank(king), file = Bits.file(king);
        int relRank = color == WHITE ? rank : 7 - rank;
        if (relRank > 1) return 0;                                  // a king that has walked out has no shelter to count
        long front = 0;                                             // the two ranks in front of the king
        for (int step = 1; step <= 2; step++) {
            int r = color == WHITE ? rank + step : rank - step;
            if (r >= 0 && r <= 7) front |= Bits.RANK_1 << (8 * r);
        }
        front &= FILE[file] | ADJACENT[file];
        int shield = Math.min(3, Bits.count(front & ownPawns));
        int bonus = shield * SHIELD_PAWN_MG;
        if ((FILE[file] & ownPawns) == 0) bonus -= OPEN_KING_FILE_MG;
        return bonus;
    }

    /**
     * In an endgame one side has won on material (a rook or more against a bare king), drive the
     * lone king towards the edge and bring the other king up. Counted for the winning side only,
     * but it is a term about the winner, not a bias towards either colour.
     */
    private static int mopUp(Position pos) {
        int whiteMaterial = nonPawnMaterial(pos, WHITE), blackMaterial = nonPawnMaterial(pos, BLACK);
        boolean whiteBare = whiteMaterial == 0 && pos.bitboard(WHITE, PAWN) == 0;
        boolean blackBare = blackMaterial == 0 && pos.bitboard(BLACK, PAWN) == 0;
        int winner;
        if (blackBare && whiteMaterial >= 500) winner = WHITE;
        else if (whiteBare && blackMaterial >= 500) winner = BLACK;
        else return 0;
        int winnerKing = pos.kingSquare(winner), loserKing = pos.kingSquare(winner ^ 1);
        int fromCentre = Math.max(3 - Bits.file(loserKing), Bits.file(loserKing) - 4)
                       + Math.max(3 - Bits.rank(loserKing), Bits.rank(loserKing) - 4);
        int apart = Math.abs(Bits.file(winnerKing) - Bits.file(loserKing))
                  + Math.abs(Bits.rank(winnerKing) - Bits.rank(loserKing));
        int bonus = 10 * fromCentre + 4 * (14 - apart);
        return winner == WHITE ? bonus : -bonus;
    }

    private static int nonPawnMaterial(Position pos, int color) {
        int total = 0;
        for (int type = KNIGHT; type <= QUEEN; type++) total += VALUE[type] * Bits.count(pos.bitboard(color, type));
        return total;
    }
}
