package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.model.*;

/**
 * Validates whether a Move is legal given the current Board.
 *
 * This is the ONLY place in the codebase that decides move legality.
 * Previously this logic was scattered across Echiquier.deplacementPossible(),
 * Piece.mouvementPossible(), and each piece subclass.
 */
public final class MoveValidator {

    private MoveValidator() {}

    /**
     * Returns true iff the move is fully legal.
     *
     * Pipeline:
     *   1. There is a piece of the active color on 'from'.
     *   2. The piece can geometrically reach 'to' (PieceType.canReach).
     *   3. The path is clear (only checked for sliders).
     *   4. The destination is empty or occupied by an enemy.
     *   5. Applying the move does not leave our own king in check.
     *   6. Special rules for castling / en-passant / promotion are satisfied.
     */
    public static boolean isLegal(Board board, Move move) {
        var pieceOpt = board.pieceAt(move.from());
        if (pieceOpt.isEmpty()) return false;

        Piece piece = pieceOpt.get();
        if (piece.color() != board.activeColor()) return false;

        // Pawn is handled separately (direction depends on color)
        if (piece.type() == PieceType.PAWN) {
            return isPawnMoveLegal(board, move, piece);
        }

        // Castling check before generic geometry
        if (piece.type() == PieceType.KING) {
            int df = Math.abs(move.from().file() - move.to().file());
            if (df == 2) return isCastlingLegal(board, move, piece);
        }

        // Generic geometry
        if (!piece.type().canReach(move.from(), move.to())) return false;

        // Path clear for sliders
        if (piece.type().isSlider() && !isPathClear(board, move.from(), move.to()))
            return false;

        // Destination must be empty or have an enemy piece
        var dest = board.pieceAt(move.to());
        if (dest.isPresent() && dest.get().color() == piece.color()) return false;

        // Apply and check king safety
        return !leavesKingInCheck(board, move);
    }

    // --- King safety -----------------------------------------------------

    /**
     * Applies the move on the board and checks if the moving side's king
     * is in check in the resulting position.
     */
    public static boolean leavesKingInCheck(Board board, Move move) {
        Board after = board.apply(move);
        Color mover = board.pieceAt(move.from()).map(Piece::color)
                          .orElseThrow();
        return CheckDetector.isInCheck(after, mover);
    }

    // --- Path clear ------------------------------------------------------

    /**
     * Returns true iff no piece stands between 'from' and 'to' (exclusive).
     * Assumes the move is a straight line (horizontal, vertical, or diagonal).
     */
    public static boolean isPathClear(Board board, Square from, Square to) {
        int df = Integer.signum(to.file() - from.file());
        int dr = Integer.signum(to.rank() - from.rank());
        int f = from.file() + df;
        int r = from.rank() + dr;
        while (f != to.file() || r != to.rank()) {
            if (board.pieceAt(new Square(f, r)).isPresent()) return false;
            f += df;
            r += dr;
        }
        return true;
    }

    // --- Castling --------------------------------------------------------

    private static boolean isCastlingLegal(Board board, Move move, Piece king) {
        Color color = king.color();
        boolean kingSide = move.to().file() > move.from().file();
        int backRank = color == Color.WHITE ? 0 : 7;

        // Castling rights
        if (!board.castlingRights().canCastle(color, kingSide)) return false;

        // King must be on its starting square
        int kingFile = 4;
        if (move.from().file() != kingFile || move.from().rank() != backRank) return false;

        // Rook must be present
        int rookFile = kingSide ? 7 : 0;
        var rookOpt = board.pieceAt(new Square(rookFile, backRank));
        if (rookOpt.isEmpty() || rookOpt.get().type() != PieceType.ROOK
                || rookOpt.get().color() != color) return false;

        // Path between king and rook must be clear
        if (!isPathClear(board, move.from(), new Square(rookFile, backRank)))
            return false;

        // King must not be in check, and must not pass through attacked squares
        if (CheckDetector.isInCheck(board, color)) return false;
        int passThroughFile = kingSide ? 5 : 3;
        Square passThrough = new Square(passThroughFile, backRank);
        // Simulate king stepping through
        Move stepThrough = Move.normal(move.from(), passThrough);
        if (leavesKingInCheck(board, stepThrough)) return false;
        // And the destination
        return !leavesKingInCheck(board, move);
    }

    // --- Pawn ------------------------------------------------------------

    private static boolean isPawnMoveLegal(Board board, Move move, Piece pawn) {
        Color color = pawn.color();
        int direction = color == Color.WHITE ? 1 : -1;
        int fromRank  = move.from().rank();
        int toRank    = move.to().rank();
        int fromFile  = move.from().file();
        int toFile    = move.to().file();

        int dr = toRank - fromRank;
        int df = Math.abs(fromFile - toFile);

        // Forward single step
        if (df == 0 && dr == direction) {
            if (board.isEmpty(move.to())) {
                return !leavesKingInCheck(board, move);
            }
            return false;
        }

        // Forward double step (only from starting rank)
        int startRank = color == Color.WHITE ? 1 : 6;
        if (df == 0 && dr == 2 * direction && fromRank == startRank) {
            Square intermediate = new Square(fromFile, fromRank + direction);
            if (board.isEmpty(intermediate) && board.isEmpty(move.to())) {
                return !leavesKingInCheck(board, move);
            }
            return false;
        }

        // Diagonal capture
        if (df == 1 && dr == direction) {
            // Normal capture
            var dest = board.pieceAt(move.to());
            if (dest.isPresent() && dest.get().color() != color) {
                return !leavesKingInCheck(board, move);
            }
            // En-passant
            var ep = board.enPassantTarget();
            if (ep.isPresent() && ep.get().equals(move.to())) {
                return !leavesKingInCheck(board, move);
            }
        }

        return false;
    }
}
