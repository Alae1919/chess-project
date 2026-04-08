package com.chess.engine.eval;

import com.chess.domain.board.Board;
import com.chess.domain.model.*;

/**
 * Heuristic position evaluator.
 *
 * Returns a score in centipawns from the perspective of 'color':
 *   positive = good for color, negative = bad.
 *
 * Replaces the Evaluation class. Key change: it works on Board (immutable)
 * rather than mutating Echiquier, and uses Color/PieceType enums.
 */
public final class Evaluator {

    public int evaluate(Board board, Color color) {
        int score = 0;
        for (int f = 0; f < Board.SIZE; f++) {
            for (int r = 0; r < Board.SIZE; r++) {
                var opt = board.pieceAt(new Square(f, r));
                if (opt.isEmpty()) continue;
                Piece p = opt.get();
                int pieceScore = p.value() + PieceSquareTables.bonus(p, f, r);
                score += p.color() == color ? pieceScore : -pieceScore;
            }
        }
        score += kingSafetyBonus(board, color);
        return score;
    }

    /**
     * Encourages the winning side to bring its king closer to the losing
     * king (endgame mating technique). Replaces forceKingToCorner().
     */
    private int kingSafetyBonus(Board board, Color color) {
        Square ourKing  = board.kingSquare(color);
        Square theirKing = board.kingSquare(color.opposite());

        int distToCenter = Math.max(3 - theirKing.file(), theirKing.file() - 4)
                         + Math.max(3 - theirKing.rank(), theirKing.rank() - 4);
        int kingDist = Math.abs(ourKing.file() - theirKing.file())
                     + Math.abs(ourKing.rank() - theirKing.rank());

        return (distToCenter + (14 - kingDist)) * 10;
    }
}
