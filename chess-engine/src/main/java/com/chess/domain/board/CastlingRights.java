package com.chess.domain.board;

import com.chess.domain.model.*;

/**
 * Immutable castling rights.
 *
 * Rights are lost when:
 *   - The king of that color moves (both sides lost).
 *   - A rook moves from its starting square (that side lost).
 *   - A rook is CAPTURED on its starting square (that side lost).
 *     This was missing in Step 1 and is fixed here (T8).
 */
public record CastlingRights(
    boolean whiteKingSide,
    boolean whiteQueenSide,
    boolean blackKingSide,
    boolean blackQueenSide
) {

    // ---- Starting squares for each rook --------------------------------
    private static final Square WHITE_KINGSIDE_ROOK  = new Square(7, 0);
    private static final Square WHITE_QUEENSIDE_ROOK = new Square(0, 0);
    private static final Square BLACK_KINGSIDE_ROOK  = new Square(7, 7);
    private static final Square BLACK_QUEENSIDE_ROOK = new Square(0, 7);

    public static CastlingRights all() {
        return new CastlingRights(true, true, true, true);
    }

    public static CastlingRights none() {
        return new CastlingRights(false, false, false, false);
    }

    public static CastlingRights fromString(String s) {
        if ("-".equals(s)) return none();
        return new CastlingRights(
            s.contains("K"), s.contains("Q"),
            s.contains("k"), s.contains("q")
        );
    }

    /**
     * Returns updated rights after a move.
     *
     * Three causes of rights loss (T8):
     *   1. King moves → both castling rights for that color are revoked.
     *   2. Rook moves from its home square → that side's right is revoked.
     *   3. A rook is captured on its home square → that side's right is revoked.
     */
    public CastlingRights update(Move move, Piece movingPiece) {
        boolean wKS = whiteKingSide;
        boolean wQS = whiteQueenSide;
        boolean bKS = blackKingSide;
        boolean bQS = blackQueenSide;

        // 1. King moves
        if (movingPiece.type() == PieceType.KING) {
            if (movingPiece.color() == Color.WHITE) { wKS = false; wQS = false; }
            else                                     { bKS = false; bQS = false; }
        }

        // 2. Rook moves from starting square
        if (movingPiece.type() == PieceType.ROOK) {
            if (move.from().equals(WHITE_KINGSIDE_ROOK))  wKS = false;
            if (move.from().equals(WHITE_QUEENSIDE_ROOK)) wQS = false;
            if (move.from().equals(BLACK_KINGSIDE_ROOK))  bKS = false;
            if (move.from().equals(BLACK_QUEENSIDE_ROOK)) bQS = false;
        }

        // 3. A rook is captured on its starting square (T8 fix)
        //    move.to() is the square of the captured piece.
        if (move.isCapture() && move.capturedPiece() != null
                && move.capturedPiece().type() == PieceType.ROOK) {
            if (move.to().equals(WHITE_KINGSIDE_ROOK))  wKS = false;
            if (move.to().equals(WHITE_QUEENSIDE_ROOK)) wQS = false;
            if (move.to().equals(BLACK_KINGSIDE_ROOK))  bKS = false;
            if (move.to().equals(BLACK_QUEENSIDE_ROOK)) bQS = false;
        }

        return new CastlingRights(wKS, wQS, bKS, bQS);
    }

    public boolean canCastle(Color color, boolean kingSide) {
        if (color == Color.WHITE) return kingSide ? whiteKingSide : whiteQueenSide;
        return kingSide ? blackKingSide : blackQueenSide;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (whiteKingSide)  sb.append('K');
        if (whiteQueenSide) sb.append('Q');
        if (blackKingSide)  sb.append('k');
        if (blackQueenSide) sb.append('q');
        return sb.isEmpty() ? "-" : sb.toString();
    }
}
