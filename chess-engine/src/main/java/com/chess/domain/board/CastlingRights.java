package com.chess.domain.board;

import com.chess.domain.model.*;

/**
 * Immutable castling rights flags.
 * Replaces the implicit "nbrCoup == 0" castling eligibility from Piece.
 */
public record CastlingRights(
    boolean whiteKingSide,
    boolean whiteQueenSide,
    boolean blackKingSide,
    boolean blackQueenSide
) {

    public static CastlingRights all() {
        return new CastlingRights(true, true, true, true);
    }

    public static CastlingRights none() {
        return new CastlingRights(false, false, false, false);
    }

    /**
     * Returns updated rights after the given move.
     * Any king or rook move revokes the relevant rights.
     */
    public CastlingRights update(Move move, Piece movingPiece) {
        boolean wKS = whiteKingSide;
        boolean wQS = whiteQueenSide;
        boolean bKS = blackKingSide;
        boolean bQS = blackQueenSide;

        if (movingPiece.type() == PieceType.KING) {
            if (movingPiece.color() == Color.WHITE) { wKS = false; wQS = false; }
            else                                     { bKS = false; bQS = false; }
        }
        if (movingPiece.type() == PieceType.ROOK) {
            Square from = move.from();
            if (from.equals(new Square(7, 0))) wKS = false;
            if (from.equals(new Square(0, 0))) wQS = false;
            if (from.equals(new Square(7, 7))) bKS = false;
            if (from.equals(new Square(0, 7))) bQS = false;
        }
        return new CastlingRights(wKS, wQS, bKS, bQS);
    }

    public boolean canCastle(Color color, boolean kingSide) {
        if (color == Color.WHITE) return kingSide ? whiteKingSide : whiteQueenSide;
        return kingSide ? blackKingSide : blackQueenSide;
    }
}
