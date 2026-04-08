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
 */
public final class Board {

    public static final int SIZE = 8;

    // 2D array: grid[file][rank], null = empty square
    private final Piece[][] grid;

    // Game metadata
    private final Color activeColor;
    private final CastlingRights castlingRights;
    private final Square enPassantTarget;  // null if none
    private final int halfMoveClock;       // for 50-move rule
    private final int fullMoveNumber;

    /** Private constructor — use static factories or FenParser. */
    Board(Piece[][] grid, Color activeColor, CastlingRights castlingRights,
          Square enPassantTarget, int halfMoveClock, int fullMoveNumber) {
        this.grid            = grid;
        this.activeColor     = activeColor;
        this.castlingRights  = castlingRights;
        this.enPassantTarget = enPassantTarget;
        this.halfMoveClock   = halfMoveClock;
        this.fullMoveNumber  = fullMoveNumber;
    }

    // --- State queries ---------------------------------------------------

    /** Returns the piece at the given square, or empty if the square is vacant. */
    public Optional<Piece> pieceAt(Square sq) {
        return Optional.ofNullable(grid[sq.file()][sq.rank()]);
    }

    public boolean isEmpty(Square sq) { return pieceAt(sq).isEmpty(); }

    public Color activeColor()           { return activeColor; }
    public CastlingRights castlingRights() { return castlingRights; }
    public Optional<Square> enPassantTarget() { return Optional.ofNullable(enPassantTarget); }
    public int halfMoveClock()           { return halfMoveClock; }
    public int fullMoveNumber()          { return fullMoveNumber; }

    /**
     * Returns the square of the king of the given color, or throws if not
     * found (should never happen in a valid position).
     */
    public Square kingSquare(Color color) {
        for (int f = 0; f < SIZE; f++) {
            for (int r = 0; r < SIZE; r++) {
                Piece p = grid[f][r];
                if (p != null && p.color() == color && p.type() == PieceType.KING) {
                    return new Square(f, r);
                }
            }
        }
        throw new IllegalStateException("No king found for " + color);
    }

    /**
     * Applies a legal move and returns the resulting Board.
     * Does NOT validate legality — call MoveValidator.isLegal() first.
     * This is the core state-transition function.
     */
    public Board apply(Move move) {
        Piece[][] next = copyGrid();
        Piece movingPiece = next[move.from().file()][move.from().rank()];

        // Move the piece
        next[move.from().file()][move.from().rank()] = null;
        next[move.to().file()][move.to().rank()] =
            movingPiece.withIncrementedMoveCount();

        // En-passant capture: remove the captured pawn on its actual square
        if (move.isEnPassant() && enPassantTarget != null) {
            next[enPassantTarget.file()][move.from().rank()] = null;
        }

        // Promotion: replace pawn with promoted piece
        if (move.isPromotion()) {
            next[move.to().file()][move.to().rank()] =
                Piece.of(movingPiece.color(), move.promotion());
        }

        // Castling: also move the rook
        if (move.isCastling()) {
            applyCastlingRook(next, move, movingPiece.color());
        }

        // Update en-passant target (only valid for pawn double push)
        Square newEpTarget = computeEnPassantTarget(move, movingPiece);

        // Update castling rights (if king or rook moved)
        CastlingRights newRights = castlingRights.update(move, movingPiece);

        // Half-move clock: reset on capture or pawn move, increment otherwise
        int newHalfMove = (move.isCapture() || movingPiece.type() == PieceType.PAWN)
            ? 0 : halfMoveClock + 1;

        // Full move number increments after Black moves
        int newFullMove = (activeColor == Color.BLACK)
            ? fullMoveNumber + 1 : fullMoveNumber;

        return new Board(next, activeColor.opposite(), newRights,
                         newEpTarget, newHalfMove, newFullMove);
    }

    // --- Helpers ---------------------------------------------------------

    private Piece[][] copyGrid() {
        Piece[][] copy = new Piece[SIZE][SIZE];
        for (int f = 0; f < SIZE; f++)
            System.arraycopy(grid[f], 0, copy[f] = new Piece[SIZE], 0, SIZE);
        return copy;
    }

    private void applyCastlingRook(Piece[][] next, Move move, Color color) {
        int rank = (color == Color.WHITE) ? 0 : 7;
        boolean kingSide = move.to().file() > move.from().file();
        int rookFromFile = kingSide ? 7 : 0;
        int rookToFile   = kingSide ? 5 : 3;
        next[rookToFile][rank] = next[rookFromFile][rank];
        next[rookFromFile][rank] = null;
        if (next[rookToFile][rank] != null)
            next[rookToFile][rank] = next[rookToFile][rank].withIncrementedMoveCount();
    }

    private Square computeEnPassantTarget(Move move, Piece movingPiece) {
        if (movingPiece.type() == PieceType.PAWN
                && Math.abs(move.from().rank() - move.to().rank()) == 2) {
            int epRank = (move.from().rank() + move.to().rank()) / 2;
            return new Square(move.from().file(), epRank);
        }
        return null;
    }

    /** Pretty-print the board for console output (replaces Echiquier.toString()). */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  a b c d e f g h\n");
        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append(" ");
            for (int file = 0; file < SIZE; file++) {
                Piece p = grid[file][rank];
                sb.append(p == null ? "." : fenChar(p)).append(" ");
            }
            sb.append(rank + 1).append("\n");
        }
        sb.append("  a b c d e f g h\n");
        return sb.toString();
    }

    private char fenChar(Piece p) {
        char c = p.type().fenChar();
        return p.color() == Color.WHITE ? c : Character.toLowerCase(c);
    }
}
