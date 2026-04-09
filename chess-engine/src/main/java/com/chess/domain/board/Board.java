package com.chess.domain.board;

import com.chess.domain.model.*;

import java.util.Optional;

/**
 * Immutable snapshot of the board state.
 *
 * Key design decisions:
 * - Immutable: apply(Move) returns a new Board instance.
 * - No rule logic: legality is in MoveValidator, generation in MoveGenerator.
 * - Replaces the mutable Echiquier + its embedded undoMove() mechanism.
 *
 * The AI search stores board references on the call stack instead of
 * mutating + undoing, which is both simpler and thread-safe.
 * 
 * apply(Move) implements all five move categories (T3–T7):
 *   T3 — normal move
 *   T4 — capture
 *   T5 — pawn promotion
 *   T6 — en passant
 *   T7 — castling (king + rook)
 *
 * Package-private constructor — use BoardFactory or FenParser to create instances.
 */
public final class Board {

    public static final int SIZE = 8;

    private final Piece[][] grid;          // grid[file][rank]
    private final Color activeColor;
    private final CastlingRights castlingRights;
    private final Square enPassantTarget;  // null when not available
    private final int halfMoveClock;
    private final int fullMoveNumber;

    // Package-private: only FenParser and Board.apply() create instances
    Board(Piece[][] grid, Color activeColor, CastlingRights castlingRights,
          Square enPassantTarget, int halfMoveClock, int fullMoveNumber) {
        this.grid            = grid;
        this.activeColor     = activeColor;
        this.castlingRights  = castlingRights;
        this.enPassantTarget = enPassantTarget;
        this.halfMoveClock   = halfMoveClock;
        this.fullMoveNumber  = fullMoveNumber;
    }

    // ---- State queries -------------------------------------------------

    public Optional<Piece> pieceAt(Square sq) {
        return Optional.ofNullable(grid[sq.file()][sq.rank()]);
    }

    public boolean isEmpty(Square sq) {
        return grid[sq.file()][sq.rank()] == null;
    }

    public Color activeColor()               { return activeColor; }
    public CastlingRights castlingRights()   { return castlingRights; }
    public Optional<Square> enPassantTarget(){ return Optional.ofNullable(enPassantTarget); }
    public int halfMoveClock()               { return halfMoveClock; }
    public int fullMoveNumber()              { return fullMoveNumber; }

    /**
     * Returns the square occupied by the king of the given color.
     * Throws if not found (invalid board state).
     */
    public Square kingSquare(Color color) {
        for (int f = 0; f < SIZE; f++) {
            for (int r = 0; r < SIZE; r++) {
                Piece p = grid[f][r];
                if (p != null && p.color() == color && p.type() == PieceType.KING)
                    return new Square(f, r);
            }
        }
        throw new IllegalStateException("No " + color + " king on the board");
    }

    // ---- State transition ----------------------------------------------

    /**
     * Applies a move (assumed legal) and returns the resulting Board.
     *
     * Move categories handled:
     *   • Normal move     (T3) — piece relocated, moveCount incremented
     *   • Capture         (T4) — captured piece removed, halfMove reset
     *   • Promotion       (T5) — pawn replaced by chosen piece type
     *   • En passant      (T6) — captured pawn removed from its real square
     *   • Castling        (T7) — king AND rook repositioned
     *
     * Does NOT validate legality — call MoveValidator.isLegal() first.
     */
    public Board apply(Move move) {
        Piece[][] next = copyGrid();

        Piece movingPiece = next[move.from().file()][move.from().rank()];
        if (movingPiece == null)
            throw new IllegalArgumentException(
                "No piece on " + move.from() + " to move");

        // --- T3 / T4: move the piece, clear source square ---------------
        next[move.from().file()][move.from().rank()] = null;
        next[move.to().file()][move.to().rank()] =
            movingPiece.withIncrementedMoveCount();

        // --- T6: en-passant capture — remove the pawn on its REAL square
        //     The captured pawn is NOT on move.to() but on the same file
        //     as the destination, same rank as the moving pawn.
        if (move.isEnPassant()) {
            int capturedPawnRank = move.from().rank(); // same rank as the attacker
            int capturedPawnFile = move.to().file();   // same file as destination
            next[capturedPawnFile][capturedPawnRank] = null;
        }

        // --- T5: promotion — replace the pawn with the chosen piece -----
        if (move.isPromotion()) {
            next[move.to().file()][move.to().rank()] =
                Piece.of(movingPiece.color(), move.promotion());
        }

        // --- T7: castling — also move the rook --------------------------
        if (move.isCastling()) {
            applyCastlingRook(next, move, movingPiece.color());
        }

        // --- Derived state ----------------------------------------------
        Square newEpTarget       = computeEnPassantTarget(move, movingPiece);
        CastlingRights newRights = castlingRights.update(move, movingPiece);

        // T4: half-move clock resets on any capture or pawn move
        int newHalfMove = (move.isCapture() || move.isEnPassant()
                            || movingPiece.type() == PieceType.PAWN)
            ? 0 : halfMoveClock + 1;

        // Full-move number increments after Black's move
        int newFullMove = (activeColor == Color.BLACK)
            ? fullMoveNumber + 1 : fullMoveNumber;

        return new Board(next, activeColor.opposite(), newRights,
                         newEpTarget, newHalfMove, newFullMove);
    }

    // ---- Private helpers -----------------------------------------------

    private Piece[][] copyGrid() {
        Piece[][] copy = new Piece[SIZE][SIZE];
        for (int f = 0; f < SIZE; f++)
            copy[f] = grid[f].clone();
        return copy;
    }

    /**
     * T7 — moves the rook to its post-castling square.
     *
     * King-side (short):  rook h-file → f-file
     * Queen-side (long):  rook a-file → d-file
     */
    private void applyCastlingRook(Piece[][] next, Move move, Color color) {
        int rank         = move.from().rank(); // same rank as the king
        boolean kingSide = move.to().file() > move.from().file();

        int rookFromFile = kingSide ? 7 : 0;
        int rookToFile   = kingSide ? 5 : 3;

        Piece rook = next[rookFromFile][rank];
        if (rook != null) {
            next[rookToFile][rank]   = rook.withIncrementedMoveCount();
            next[rookFromFile][rank] = null;
        }
    }

    /**
     * Computes the en-passant target square after a pawn double push.
     * Returns null for all other move types.
     */
    private Square computeEnPassantTarget(Move move, Piece movingPiece) {
        if (movingPiece.type() == PieceType.PAWN
                && Math.abs(move.from().rank() - move.to().rank()) == 2) {
            // The ep target is the square the pawn skipped over
            int epRank = (move.from().rank() + move.to().rank()) / 2;
            return new Square(move.from().file(), epRank);
        }
        return null;
    }

    // ---- Display -------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  a b c d e f g h\n");
        for (int rank = SIZE - 1; rank >= 0; rank--) {
            sb.append(rank + 1).append(" ");
            for (int file = 0; file < SIZE; file++) {
                Piece p = grid[file][rank];
                if (p == null) {
                    sb.append(". ");
                } else {
                    char c = p.type().fenChar();
                    sb.append(p.color() == Color.WHITE ? c
                                                       : Character.toLowerCase(c))
                      .append(" ");
                }
            }
            sb.append(rank + 1).append("\n");
        }
        sb.append("  a b c d e f g h\n");
        return sb.toString();
    }
}
