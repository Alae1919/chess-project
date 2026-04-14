package com.chess.engine.eval;

import com.chess.domain.board.Board;
import com.chess.domain.model.*;

/**
 * Piece mobility evaluation.
 *
 * Counts the number of squares each piece can move to (pseudo-legally — no
 * king-safety filter). A higher mobility indicates better piece activity.
 *
 * Bonus per reachable square, by piece type:
 *   Knight: 4 cp    Bishop: 3 cp    Rook: 2 cp    Queen: 1 cp
 *   (Pawns and King are not counted — their mobility is captured elsewhere.)
 *
 * Returns the score from {@code color}'s perspective
 * (color's mobility bonus − opponent's mobility bonus).
 *
 * Using pseudo-legal generation (no isLegal() call) is ~10× faster than full
 * legal move generation and gives a good enough approximation for evaluation.
 */
public final class MobilityEvaluator {

    private static final int KNIGHT_BONUS = 4;
    private static final int BISHOP_BONUS = 3;
    private static final int ROOK_BONUS   = 2;
    private static final int QUEEN_BONUS  = 1;

    private MobilityEvaluator() {}

    /** Score from {@code color}'s perspective (our mobility − their mobility). */
    public static int evaluate(Board board, Color color) {
        return mobilityFor(board, color) - mobilityFor(board, color.opposite());
    }

    private static int mobilityFor(Board board, Color color) {
        int score = 0;
        for (int f = 0; f < Board.SIZE; f++) {
            for (int r = 0; r < Board.SIZE; r++) {
                var opt = board.pieceAt(new Square(f, r));
                if (opt.isEmpty() || opt.get().color() != color) continue;
                Piece p = opt.get();
                score += switch (p.type()) {
                    case KNIGHT -> countKnightMoves(board, f, r, color) * KNIGHT_BONUS;
                    case BISHOP -> countRayMoves(board, f, r, color,
                                                 new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}}) * BISHOP_BONUS;
                    case ROOK   -> countRayMoves(board, f, r, color,
                                                 new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) * ROOK_BONUS;
                    case QUEEN  -> countRayMoves(board, f, r, color,
                                                 new int[][]{{1,0},{-1,0},{0,1},{0,-1},
                                                             {1,1},{1,-1},{-1,1},{-1,-1}}) * QUEEN_BONUS;
                    default     -> 0;
                };
            }
        }
        return score;
    }

    private static int countKnightMoves(Board board, int f, int r, Color color) {
        int count = 0;
        int[][] deltas = {{2,1},{2,-1},{-2,1},{-2,-1},{1,2},{1,-2},{-1,2},{-1,-2}};
        for (int[] d : deltas) {
            int nf = f + d[0], nr = r + d[1];
            if (nf < 0 || nf > 7 || nr < 0 || nr > 7) continue;
            var target = board.pieceAt(new Square(nf, nr));
            // Can move to empty squares or enemy-occupied squares
            if (target.isEmpty() || target.get().color() != color) count++;
        }
        return count;
    }

    /**
     * Counts squares reachable by a sliding piece along the given directions.
     * Stops at board edge; stops after capturing an enemy piece; skips friendly pieces.
     */
    private static int countRayMoves(Board board, int f, int r, Color color, int[][] dirs) {
        int count = 0;
        for (int[] d : dirs) {
            int nf = f + d[0], nr = r + d[1];
            while (nf >= 0 && nf <= 7 && nr >= 0 && nr <= 7) {
                var target = board.pieceAt(new Square(nf, nr));
                if (target.isPresent()) {
                    if (target.get().color() != color) count++; // capture square counts
                    break; // ray blocked
                }
                count++;
                nf += d[0];
                nr += d[1];
            }
        }
        return count;
    }
}
