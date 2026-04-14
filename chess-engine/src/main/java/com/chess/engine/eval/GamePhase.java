package com.chess.engine.eval;

import com.chess.domain.board.Board;
import com.chess.domain.model.*;

/**
 * Detects the game phase (opening/midgame/endgame) based on remaining non-pawn material.
 *
 * Returns a continuous phase factor 0–256:
 *   256 = pure midgame  (both sides have full piece complement)
 *   0   = pure endgame  (very little material left)
 *
 * The factor is used for tapered evaluation: interpolating between midgame and
 * endgame piece-square tables (and other phase-dependent terms) avoids sharp
 * discontinuities at material thresholds.
 */
public final class GamePhase {

    // Non-pawn, non-king material thresholds (centipawns).
    // Full set: Q(900) + 2R(1000) + 2B(660) + 2N(640) per side × 2 sides ≈ 6400 total
    private static final int MIDGAME_MATERIAL = 6200;
    private static final int ENDGAME_MATERIAL = 1300;

    private GamePhase() {}

    /**
     * Returns the phase factor: 256 = midgame, 0 = endgame.
     * Uses only non-pawn, non-king material to avoid pawn captures shifting the phase.
     */
    public static int phase(Board board) {
        int material = 0;
        for (int f = 0; f < Board.SIZE; f++) {
            for (int r = 0; r < Board.SIZE; r++) {
                var opt = board.pieceAt(new Square(f, r));
                if (opt.isEmpty()) continue;
                PieceType t = opt.get().type();
                if (t != PieceType.PAWN && t != PieceType.KING) {
                    material += opt.get().value();
                }
            }
        }
        // Clamp to [0, 256]
        int range = MIDGAME_MATERIAL - ENDGAME_MATERIAL;
        return Math.min(256, Math.max(0,
            256 * (material - ENDGAME_MATERIAL) / range));
    }

    /** Convenience: returns true when the position is clearly an endgame. */
    public static boolean isEndgame(Board board) {
        return phase(board) < 64;
    }
}
