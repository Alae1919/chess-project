package com.chess.domain.model;

import java.util.Objects;

/**
 * An immutable record of a single half-move.
 *
 * Special moves are distinguished by flags rather than by subclassing,
 * keeping serialisation and equality simple.
 */
public final class Move {

    private final Square from;
    private final Square to;
    private final Piece capturedPiece;   // null if not a capture
    private final PieceType promotion;   // null unless pawn promotion
    private final boolean castling;
    private final boolean enPassant;

    /** Standard move, no capture. */
    public Move(Square from, Square to) {
        this(from, to, null, null, false, false);
    }

    /** Capture move. */
    public Move(Square from, Square to, Piece capturedPiece) {
        this(from, to, capturedPiece, null, false, false);
    }

    /** Full constructor. */
    public Move(Square from, Square to, Piece capturedPiece,
                PieceType promotion, boolean castling, boolean enPassant) {
        this.from          = Objects.requireNonNull(from);
        this.to            = Objects.requireNonNull(to);
        this.capturedPiece = capturedPiece;
        this.promotion     = promotion;
        this.castling      = castling;
        this.enPassant     = enPassant;
    }

    // --- Factory helpers -------------------------------------------------

    public static Move normal(Square from, Square to) {
        return new Move(from, to);
    }

    public static Move capture(Square from, Square to, Piece captured) {
        return new Move(from, to, captured);
    }

    public static Move castling(Square from, Square to) {
        return new Move(from, to, null, null, true, false);
    }

    public static Move enPassant(Square from, Square to, Piece captured) {
        return new Move(from, to, captured, null, false, true);
    }

    public static Move promotion(Square from, Square to,
                                 Piece captured, PieceType promoteType) {
        return new Move(from, to, captured, promoteType, false, false);
    }

    // --- Accessors -------------------------------------------------------

    public Square from()           { return from; }
    public Square to()             { return to; }
    public Piece  capturedPiece()  { return capturedPiece; }
    public PieceType promotion()   { return promotion; }
    public boolean isCastling()    { return castling; }
    public boolean isEnPassant()   { return enPassant; }
    public boolean isCapture()     { return capturedPiece != null; }
    public boolean isPromotion()   { return promotion != null; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Move m)) return false;
        return from.equals(m.from) && to.equals(m.to);
    }

    @Override
    public int hashCode() { return Objects.hash(from, to); }

    @Override
    public String toString() {
        String s = from + "" + to;
        if (promotion != null) s += "=" + promotion.fenChar();
        return s;
    }
}
