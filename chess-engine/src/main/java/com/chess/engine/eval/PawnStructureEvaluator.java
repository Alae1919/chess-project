package com.chess.engine.eval;

import com.chess.domain.board.Board;
import com.chess.domain.model.*;

/**
 * Pawn structure evaluation.
 *
 * Detects and penalises/rewards the following structural features:
 *   • Doubled pawns  — two pawns on the same file               (−15 cp each)
 *   • Isolated pawns — no friendly pawn on adjacent files        (−20 cp each)
 *   • Passed pawns   — no enemy pawn can block or capture them   (+20 cp × rank bonus)
 *
 * Uses a single O(64) scan to build per-file pawn bitmaps before evaluating,
 * avoiding repeated traversal.
 */
public final class PawnStructureEvaluator {

    private static final int DOUBLED_PAWN_PENALTY  = -15;
    private static final int ISOLATED_PAWN_PENALTY = -20;
    private static final int PASSED_PAWN_BASE      =  20;

    private PawnStructureEvaluator() {}

    /**
     * Returns the pawn structure score from {@code color}'s perspective.
     * Call for both colors and subtract to get a relative score.
     */
    public static int evaluate(Board board, Color color) {
        // Count friendly pawns per file (index 0 = a-file)
        int[] friendlyCount = new int[8];
        // For passed pawn detection: most-advanced friendly pawn rank per file
        int[] friendlyMaxRank = new int[8];
        // For passed pawn detection: most-advanced enemy pawn rank per file
        // (in terms of advancement toward promotion — i.e. toward rank 8 for white)
        int[] enemyMinBlockRank = new int[8]; // rank closest to our promotion rank

        for (int i = 0; i < 8; i++) {
            friendlyMaxRank[i]  = -1;
            enemyMinBlockRank[i] = 8;  // sentinel: no enemy blocker found yet
        }

        // Single O(64) scan
        for (int f = 0; f < Board.SIZE; f++) {
            for (int r = 0; r < Board.SIZE; r++) {
                var opt = board.pieceAt(new Square(f, r));
                if (opt.isEmpty() || opt.get().type() != PieceType.PAWN) continue;
                Piece p = opt.get();
                if (p.color() == color) {
                    friendlyCount[f]++;
                    // Advancement rank: white advances upward (higher rank = better)
                    //                   black advances downward (lower rank = better)
                    int advance = (color == Color.WHITE) ? r : (7 - r);
                    if (advance > friendlyMaxRank[f]) friendlyMaxRank[f] = advance;
                } else {
                    // Enemy pawn: record how far it blocks our passage
                    // For white, an enemy pawn on rank r blocks on file f at distance r
                    // For black, enemy pawn on rank r blocks at distance (7-r)
                    int blockAdvance = (color == Color.WHITE) ? r : (7 - r);
                    if (blockAdvance < enemyMinBlockRank[f]) {
                        enemyMinBlockRank[f] = blockAdvance;
                    }
                }
            }
        }

        int score = 0;

        for (int f = 0; f < 8; f++) {
            if (friendlyCount[f] == 0) continue;

            // Doubled pawns: penalise every extra pawn on this file
            if (friendlyCount[f] > 1) {
                score += DOUBLED_PAWN_PENALTY * (friendlyCount[f] - 1);
            }

            // Isolated pawns: no friendly pawn on adjacent files
            boolean leftEmpty  = (f == 0 || friendlyCount[f - 1] == 0);
            boolean rightEmpty = (f == 7 || friendlyCount[f + 1] == 0);
            if (leftEmpty && rightEmpty) {
                score += ISOLATED_PAWN_PENALTY;
            }

            // Passed pawns: our most advanced pawn on this file must not be
            // blocked by any enemy pawn on this file or adjacent files.
            if (friendlyMaxRank[f] >= 0) {
                boolean passed = true;
                for (int ef = Math.max(0, f - 1); ef <= Math.min(7, f + 1); ef++) {
                    // If an enemy pawn is ahead of (or at) our pawn, it's not passed
                    if (enemyMinBlockRank[ef] > friendlyMaxRank[f]) {
                        passed = false;
                        break;
                    }
                }
                if (passed) {
                    // Bonus scales with how advanced the pawn is (rank 1=min, rank 6=max)
                    score += PASSED_PAWN_BASE * (friendlyMaxRank[f] + 1);
                }
            }
        }

        return score;
    }
}
