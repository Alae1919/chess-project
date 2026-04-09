package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.model.*;

/**
 * The single authority on move legality.
 *
 * T9 — explicit guards added for:
 *   • Empty source square
 *   • Moving the opponent's piece
 *   • Capturing a friendly piece
 *   • Geometrically invalid move
 *   • Move that leaves own king in check
 *
 * T10 — isPawnMoveLegal() fully covers:
 *   • Single step forward
 *   • Double step from starting rank only
 *   • Diagonal capture (enemy piece present)
 *   • En passant
 *   • Promotion (any target on back rank)
 *   • Backward moves forbidden
 *   • Sideways moves forbidden
 */
public final class MoveValidator {

    private MoveValidator() {}

    // ---- Main entry point ----------------------------------------------

    /**
     * Returns true iff the move is fully legal on the given board.
     */
    public static boolean isLegal(Board board, Move move) {
        // T9-1: source square must contain a piece
        var pieceOpt = board.pieceAt(move.from());
        if (pieceOpt.isEmpty()) return false;

        Piece piece = pieceOpt.get();

        // T9-2: piece must belong to the active player
        if (piece.color() != board.activeColor()) return false;

        // Delegate pawn logic (T10)
        if (piece.type() == PieceType.PAWN) {
            return isPawnMoveLegal(board, move, piece);
        }

        // Castling is detected by king geometry (2-square horizontal)
        if (piece.type() == PieceType.KING) {
            int df = Math.abs(move.from().file() - move.to().file());
            if (df == 2) return isCastlingLegal(board, move, piece);
        }

        // T9-4: geometry check
        if (!piece.type().canReach(move.from(), move.to())) return false;

        // T9-3 + path obstruction
        var dest = board.pieceAt(move.to());
        if (dest.isPresent() && dest.get().color() == piece.color()) return false;
        if (piece.type().isSlider() && !isPathClear(board, move.from(), move.to()))
            return false;

        // T9-5: king must not be in check after the move
        return !leavesKingInCheck(board, move);
    }

    // ---- King safety ---------------------------------------------------

    /**
     * Applies the move and checks whether it leaves the mover's king in check.
     */
    public static boolean leavesKingInCheck(Board board, Move move) {
        Board after = board.apply(move);
        Color mover = board.pieceAt(move.from())
            .orElseThrow(() -> new IllegalArgumentException(
                "No piece on " + move.from()))
            .color();
        return CheckDetector.isInCheck(after, mover);
    }

    // ---- Path obstruction ----------------------------------------------

    /**
     * Returns true iff no piece stands between from and to (exclusive).
     * Assumes the two squares are on the same rank, file, or diagonal.
     */
    public static boolean isPathClear(Board board, Square from, Square to) {
        int df = Integer.signum(to.file() - from.file());
        int dr = Integer.signum(to.rank() - from.rank());
        int f  = from.file() + df;
        int r  = from.rank() + dr;
        while (f != to.file() || r != to.rank()) {
            if (board.pieceAt(new Square(f, r)).isPresent()) return false;
            f += df;
            r += dr;
        }
        return true;
    }

    // ---- Castling ------------------------------------------------------

    private static boolean isCastlingLegal(Board board, Move move, Piece king) {
        Color color    = king.color();
        int   backRank = color == Color.WHITE ? 0 : 7;
        boolean kingSide = move.to().file() > move.from().file();

        // King must be on e1/e8
        if (move.from().file() != 4 || move.from().rank() != backRank) return false;

        // Castling rights must still be available
        if (!board.castlingRights().canCastle(color, kingSide)) return false;

        // The correct rook must be on its starting square
        int rookFile = kingSide ? 7 : 0;
        var rookOpt  = board.pieceAt(new Square(rookFile, backRank));
        if (rookOpt.isEmpty()
                || rookOpt.get().type()  != PieceType.ROOK
                || rookOpt.get().color() != color) return false;

        // Path between king and rook must be clear
        if (!isPathClear(board, move.from(), new Square(rookFile, backRank)))
            return false;

        // King must not currently be in check
        if (CheckDetector.isInCheck(board, color)) return false;

        // King must not pass through an attacked square
        int passThroughFile = kingSide ? 5 : 3;
        Move stepThrough = Move.normal(move.from(),
                                       new Square(passThroughFile, backRank));
        if (leavesKingInCheck(board, stepThrough)) return false;

        // King's destination must not be attacked
        return !leavesKingInCheck(board, move);
    }

    // ---- Pawn (T10) ---------------------------------------------------

    /**
     * Full pawn move validation.
     *
     * Rules enforced:
     *   1. Forward single step — destination must be empty.
     *   2. Forward double step — only from starting rank, both squares empty.
     *   3. Diagonal capture   — exactly one enemy piece on destination.
     *   4. En passant         — ep target square must match board state.
     *   5. Promotion          — any forward move reaching the back rank.
     *   6. Backward moves     — always illegal (direction check).
     *   7. Sideways moves     — always illegal (|df|>1 or |df|==1 without capture).
     */
    static boolean isPawnMoveLegal(Board board, Move move, Piece pawn) {
        Color color     = pawn.color();
        int   direction = color == Color.WHITE ? 1 : -1;
        int   startRank = color == Color.WHITE ? 1 : 6;
        int   backRank  = color == Color.WHITE ? 7 : 0;

        int fromFile = move.from().file();
        int fromRank = move.from().rank();
        int toFile   = move.to().file();
        int toRank   = move.to().rank();

        int dr = toRank - fromRank;
        int df = Math.abs(fromFile - toFile);

        // T10-6: backward moves are always illegal
        if (Integer.signum(dr) != direction) return false;

        // T10-7: file difference must be 0 (straight) or 1 (diagonal capture)
        if (df > 1) return false;

        if (df == 0) {
            // ----- Straight pawn moves -----------------------------------

            // T10-1: single step
            if (dr == direction) {
                if (!board.isEmpty(move.to())) return false;
                // T10-5: promotion check (back rank)
                // The move itself is legal; promotion type is part of Move metadata
                return !leavesKingInCheck(board, move);
            }

            // T10-2: double step
            if (dr == 2 * direction) {
                if (fromRank != startRank) return false;
                Square intermediate = new Square(fromFile, fromRank + direction);
                if (!board.isEmpty(intermediate) || !board.isEmpty(move.to()))
                    return false;
                return !leavesKingInCheck(board, move);
            }

            // Any other straight delta is illegal (e.g. triple push)
            return false;
        }

        // ----- Diagonal pawn moves (df == 1) ----------------------------

        if (dr != direction) return false; // diagonal must be forward

        // T10-3: normal diagonal capture
        var dest = board.pieceAt(move.to());
        if (dest.isPresent() && dest.get().color() != color) {
            return !leavesKingInCheck(board, move);
        }

        // T10-4: en passant
        var epOpt = board.enPassantTarget();
        if (epOpt.isPresent() && epOpt.get().equals(move.to())) {
            // Build the proper en-passant Move with the captured pawn
            Square capturedPawnSquare = new Square(toFile, fromRank);
            Piece  capturedPawn       = board.pieceAt(capturedPawnSquare).orElse(null);
            Move   epMove = Move.enPassant(move.from(), move.to(), capturedPawn);
            return !leavesKingInCheck(board, epMove);
        }

        // Diagonal without a capturable piece or ep target is illegal (T10-7)
        return false;
    }
}


