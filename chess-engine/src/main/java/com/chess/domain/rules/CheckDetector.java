package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.model.*;

/**
 * Detects check, checkmate, and stalemate conditions.
 * All methods are pure functions over a Board — no state.
 */
public final class CheckDetector {

    private CheckDetector() {}

    /**
     * Returns true iff the king of the given color is in check on this board.
     * Iterates over all enemy pieces and checks whether any can capture the king.
     */
    public static boolean isInCheck(Board board, Color color) {
        Square kingSquare = board.kingSquare(color);
        Color enemy = color.opposite();

        for (int f = 0; f < Board.SIZE; f++) {
            for (int r = 0; r < Board.SIZE; r++) {
                var sq = new Square(f, r);
                var pieceOpt = board.pieceAt(sq);
                if (pieceOpt.isEmpty() || pieceOpt.get().color() != enemy) continue;

                Piece p = pieceOpt.get();
                if (p.type() == PieceType.PAWN) {
                    if (pawnAttacks(sq, kingSquare, enemy)) return true;
                } else {
                    if (p.type().canReach(sq, kingSquare)) {
                        if (!p.type().isSlider()
                            || MoveValidator.isPathClear(board, sq, kingSquare))
                            return true;
                    }
                }
            }
        }
        return false;
    }

    /** Returns true if a pawn of the given color at 'from' attacks 'target'. */
    private static boolean pawnAttacks(Square from, Square target, Color color) {
        int direction = color == Color.WHITE ? 1 : -1;
        int dr = target.rank() - from.rank();
        int df = Math.abs(target.file() - from.file());
        return dr == direction && df == 1;
    }
}
