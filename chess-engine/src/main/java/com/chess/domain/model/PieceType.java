package com.chess.domain.model;

/**
 * Enumerates all chess piece types and encapsulates their movement geometry.
 *
 * canReach(Square from, Square to) answers: "Is this destination reachable by
 * the piece's movement pattern, ignoring board state?"
 *
 * Board-level concerns (path clear, king safety) are handled by MoveValidator.
 */
public enum PieceType {

    PAWN {
        /**
         * Pawn geometry is color-dependent and is handled specially:
         * forward single/double step and diagonal capture geometry.
         * En-passant and promotion are handled by Move subtypes.
         * This method returns false for pawns; MoveGenerator handles them
         * with explicit pawn logic that needs color context.
         */
        @Override
        public boolean canReach(Square from, Square to) {
            // Pawn geometry is color-dependent; delegate to the generator
            throw new UnsupportedOperationException(
                "Use PawnMoveGenerator for pawn geometry"
            );
        }
        @Override public boolean isSlider() { return false; }
    },

    ROOK {
        @Override
        public boolean canReach(Square from, Square to) {
            if (from.equals(to)) return false;
            return from.file() == to.file() || from.rank() == to.rank();
        }
        @Override public boolean isSlider() { return true; }
    },

    BISHOP {
        @Override
        public boolean canReach(Square from, Square to) {
            int df = Math.abs(from.file() - to.file());
            int dr = Math.abs(from.rank() - to.rank());
            return df == dr && df != 0;
        }
        @Override public boolean isSlider() { return true; }
    },

    KNIGHT {
        @Override
        public boolean canReach(Square from, Square to) {
            int df = Math.abs(from.file() - to.file());
            int dr = Math.abs(from.rank() - to.rank());
            return (df == 2 && dr == 1) || (df == 1 && dr == 2);
        }
        /** Knight jumps: path-clear check is skipped by MoveValidator. */
        @Override public boolean isSlider() { return false; }
    },

    QUEEN {
        @Override
        public boolean canReach(Square from, Square to) {
            int df = Math.abs(from.file() - to.file());
            int dr = Math.abs(from.rank() - to.rank());
            return (df != 0 && dr == 0) || (df == 0 && dr != 0) || (df == dr && df != 0);
        }
        @Override public boolean isSlider() { return true; }
    },

    KING {
        @Override
        public boolean canReach(Square from, Square to) {
            int df = Math.abs(from.file() - to.file());
            int dr = Math.abs(from.rank() - to.rank());
            // Normal king move (1 square any direction)
            if (df <= 1 && dr <= 1 && (df + dr) > 0) return true;
            // Castling: king moves 2 squares horizontally
            // Full legality (rook present, path clear, not in check) is in
            // CastlingValidator, not here.
            return dr == 0 && df == 2;
        }
        @Override public boolean isSlider() { return false; }
    };

    /**
     * Returns true if this piece slides along a ray (can be blocked).
     * False for KNIGHT and KING (and PAWN which is handled separately).
     */
    public abstract boolean isSlider();

    /**
     * Pure geometry check: can this piece type reach 'to' from 'from'?
     * Ignores board occupancy, color, king safety.
     */
    public abstract boolean canReach(Square from, Square to);

    /** Material value in centipawns (standard values). */
    public int value() {
        return switch (this) {
            case PAWN   -> 100;
            case KNIGHT -> 320;
            case BISHOP -> 330;
            case ROOK   -> 500;
            case QUEEN  -> 900;
            case KING   -> 20000;
        };
    }

    /** FEN character for this piece type (uppercase = white convention). */
    public char fenChar() {
        return switch (this) {
            case PAWN   -> 'P';
            case ROOK   -> 'R';
            case KNIGHT -> 'N';
            case BISHOP -> 'B';
            case QUEEN  -> 'Q';
            case KING   -> 'K';
        };
    }
}
