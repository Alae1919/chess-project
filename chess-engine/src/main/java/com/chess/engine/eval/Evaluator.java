package com.chess.engine.eval;

import com.chess.domain.board.Board;
import com.chess.domain.model.*;

/**
 * Heuristic position evaluator.
 *
 * Returns a score in centipawns from the perspective of {@code color}:
 *   positive = good for color, negative = bad.
 *
 * Evaluation components:
 *   1. Material + tapered piece-square tables  (phase-interpolated MG/EG)
 *   2. Pawn structure                          (doubled, isolated, passed pawns)
 *   3. Piece mobility                          (pseudo-legal move count bonus)
 *   4. Bishop pair bonus
 *   5. King safety / centralisation            (phase-gated: only in MG/EG respectively)
 */
public final class Evaluator implements PositionEvaluator {

    private static final int BISHOP_PAIR_BONUS = 30; // cp bonus for having both bishops

    public int evaluate(Board board, Color color) {
        int phase = GamePhase.phase(board);

        int score = materialAndPst(board, color, phase);

        score += PawnStructureEvaluator.evaluate(board, color);
        score -= PawnStructureEvaluator.evaluate(board, color.opposite());

        score += MobilityEvaluator.evaluate(board, color);

        score += bishopPairBonus(board, color);
        score -= bishopPairBonus(board, color.opposite());

        // King safety is most relevant in the midgame; king centralisation is handled
        // by the endgame PST, so only apply the proximity bonus in midgame.
        if (phase > 64) {
            score += kingSafetyBonus(board, color);
        }

        return score;
    }

    // -----------------------------------------------------------------------

    private int materialAndPst(Board board, Color color, int phase) {
        int score = 0;
        for (int f = 0; f < Board.SIZE; f++) {
            for (int r = 0; r < Board.SIZE; r++) {
                var opt = board.pieceAt(new Square(f, r));
                if (opt.isEmpty()) continue;
                Piece p = opt.get();
                int pieceScore = p.value() + PieceSquareTables.bonus(p, f, r, phase);
                score += p.color() == color ? pieceScore : -pieceScore;
            }
        }
        return score;
    }

    private int bishopPairBonus(Board board, Color color) {
        int bishops = 0;
        for (int f = 0; f < Board.SIZE; f++) {
            for (int r = 0; r < Board.SIZE; r++) {
                var opt = board.pieceAt(new Square(f, r));
                if (opt.isPresent()
                        && opt.get().color() == color
                        && opt.get().type() == PieceType.BISHOP) {
                    bishops++;
                }
            }
        }
        return (bishops >= 2) ? BISHOP_PAIR_BONUS : 0;
    }

    /**
     * Encourages the winning side to bring its king closer to the losing king
     * (endgame mating technique). Applied only during midgame (phase > 64).
     */
    private int kingSafetyBonus(Board board, Color color) {
        Square ourKing   = board.kingSquare(color);
        Square theirKing = board.kingSquare(color.opposite());

        int distToCenter = Math.max(3 - theirKing.file(), theirKing.file() - 4)
                         + Math.max(3 - theirKing.rank(), theirKing.rank() - 4);
        int kingDist     = Math.abs(ourKing.file() - theirKing.file())
                         + Math.abs(ourKing.rank() - theirKing.rank());

        return (distToCenter + (14 - kingDist)) * 10;
    }
}
