package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;
import com.chess.domain.model.Piece;
import com.chess.domain.model.PieceType;
import com.chess.domain.model.Square;

/**
 * Dead positions where no sequence of legal moves can produce checkmate (FIDE 5.2.2):
 * bare kings, a king and one minor piece against a bare king, or only bishops that all
 * stand on squares of one colour. A knight against a knight (or any pawn, rook or
 * queen) can still mate, so those are not drawn automatically.
 */
public final class InsufficientMaterial {

    private InsufficientMaterial() {}

    /**
     * Whether {@code side}'s own pieces can never deliver mate: a bare king or a king
     * with a single minor piece. Used when the other side runs out of time, which is
     * a draw rather than a loss if this side could not have won anyway.
     */
    public static boolean cannotMate(Board board, Color side) {
        int minors = 0;
        for (int file = 0; file < Board.SIZE; file++) {
            for (int rank = 0; rank < Board.SIZE; rank++) {
                Piece piece = board.pieceAt(new Square(file, rank)).orElse(null);
                if (piece == null || piece.color() != side || piece.type() == PieceType.KING) continue;
                if (piece.type() != PieceType.KNIGHT && piece.type() != PieceType.BISHOP) return false;
                minors++;
            }
        }
        return minors <= 1;
    }

    public static boolean isDead(Board board) {
        int minors = 0;
        int lightBishops = 0;
        int darkBishops = 0;

        for (int file = 0; file < Board.SIZE; file++) {
            for (int rank = 0; rank < Board.SIZE; rank++) {
                var square = new Square(file, rank);
                Piece piece = board.pieceAt(square).orElse(null);
                if (piece == null || piece.type() == PieceType.KING) continue;

                switch (piece.type()) {
                    case KNIGHT -> minors++;
                    case BISHOP -> {
                        minors++;
                        if ((file + rank) % 2 == 0) darkBishops++; else lightBishops++;
                    }
                    default -> { return false; } // pawn, rook or queen
                }
            }
        }

        if (minors <= 1) return true;
        // Only bishops, all on one colour of square (a knight among them is never dead)
        return minors == lightBishops + darkBishops && (lightBishops == 0 || darkBishops == 0);
    }
}
