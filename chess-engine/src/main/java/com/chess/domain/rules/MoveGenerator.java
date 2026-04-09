package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates all pseudo-legal moves and filters them through MoveValidator
 * to return only fully legal moves.
 *
 * Replaces Piece.getMoves() and all the getMovesNord/Sud/Est/Ouest methods.
 * Those direction helpers were repetitive; we now use a unified ray-casting
 * approach for sliders.
 */
public final class MoveGenerator {

    private MoveGenerator() {}

    /** All legal moves for the active color on this board. */
    public static List<Move> generateLegalMoves(Board board) {
        return generateLegalMoves(board, board.activeColor());
    }

    /** All legal moves for a specific color (used by AI for both sides). */
    public static List<Move> generateLegalMoves(Board board, Color color) {
        List<Move> moves = new ArrayList<>();
        for (int f = 0; f < Board.SIZE; f++) {
            for (int r = 0; r < Board.SIZE; r++) {
                var sq = new Square(f, r);
                var pieceOpt = board.pieceAt(sq);
                if (pieceOpt.isEmpty() || pieceOpt.get().color() != color) continue;
                generateForPiece(board, sq, pieceOpt.get(), moves);
            }
        }
        return moves;
    }

    // --- Per-piece generation --------------------------------------------

    private static void generateForPiece(Board board, Square from,
                                          Piece piece, List<Move> moves) {
        switch (piece.type()) {
            case PAWN   -> generatePawnMoves(board, from, piece, moves);
            case KNIGHT -> generateKnightMoves(board, from, piece, moves);
            case BISHOP -> generateSlidingMoves(board, from, piece,
                               new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}}, moves);
            case ROOK   -> generateSlidingMoves(board, from, piece,
                               new int[][]{{1,0},{-1,0},{0,1},{0,-1}}, moves);
            case QUEEN  -> generateSlidingMoves(board, from, piece,
                               new int[][]{{1,0},{-1,0},{0,1},{0,-1},
                                           {1,1},{1,-1},{-1,1},{-1,-1}}, moves);
            case KING   -> generateKingMoves(board, from, piece, moves);
        }
    }

    private static void generateSlidingMoves(Board board, Square from,
                                              Piece piece, int[][] dirs,
                                              List<Move> moves) {
        for (int[] dir : dirs) {
            int f = from.file() + dir[0];
            int r = from.rank() + dir[1];
            while (f >= 0 && f < Board.SIZE && r >= 0 && r < Board.SIZE) {
                Square to = new Square(f, r);
                tryAdd(board, from, to, piece, moves);
                if (board.pieceAt(to).isPresent()) break; // blocked
                f += dir[0];
                r += dir[1];
            }
        }
    }

    private static void generateKnightMoves(Board board, Square from,
                                             Piece piece, List<Move> moves) {
        int[][] offsets = {{2,1},{2,-1},{-2,1},{-2,-1},{1,2},{1,-2},{-1,2},{-1,-2}};
        for (int[] off : offsets) {
            int f = from.file() + off[0];
            int r = from.rank() + off[1];
            if (f >= 0 && f < Board.SIZE && r >= 0 && r < Board.SIZE)
                tryAdd(board, from, new Square(f, r), piece, moves);
        }
    }

    private static void generateKingMoves(Board board, Square from,
                                           Piece piece, List<Move> moves) {
        for (int df = -1; df <= 1; df++) {
            for (int dr = -1; dr <= 1; dr++) {
                if (df == 0 && dr == 0) continue;
                int f = from.file() + df;
                int r = from.rank() + dr;
                if (f >= 0 && f < Board.SIZE && r >= 0 && r < Board.SIZE)
                    tryAdd(board, from, new Square(f, r), piece, moves);
            }
        }
        // Castling
        int backRank = piece.color() == Color.WHITE ? 0 : 7;
        tryAdd(board, from, new Square(6, backRank), piece, moves); // king-side
        tryAdd(board, from, new Square(2, backRank), piece, moves); // queen-side
    }

    private static void generatePawnMoves(Board board, Square from,
                                           Piece piece, List<Move> moves) {
        Color color = piece.color();
        int dir = color == Color.WHITE ? 1 : -1;
        int promotionRank = color == Color.WHITE ? 7 : 0;

        // Single step
        Square one = new Square(from.file(), from.rank() + dir);
        if (inBounds(one) && board.isEmpty(one)) {
            addPawnMove(board, from, one, piece, promotionRank, moves);
            // Double step
            int startRank = color == Color.WHITE ? 1 : 6;
            if (from.rank() == startRank) {
                Square two = new Square(from.file(), from.rank() + 2 * dir);
                if (board.isEmpty(two))
                    addPawnMove(board, from, two, piece, promotionRank, moves);
            }
        }

        // Captures
        for (int df : new int[]{-1, 1}) {
            int f = from.file() + df;
            if (f < 0 || f >= Board.SIZE) continue;
            Square cap = new Square(f, from.rank() + dir);
            var dest = board.pieceAt(cap);
            if (dest.isPresent() && dest.get().color() != color)
                addPawnMove(board, from, cap, piece, promotionRank, moves);
            // En-passant
            var ep = board.enPassantTarget();
            if (ep.isPresent() && ep.get().equals(cap)) {
                Move m = Move.enPassant(from, cap,
                    board.pieceAt(new Square(f, from.rank())).orElse(null));
                if (MoveValidator.isLegal(board, m)) moves.add(m);
            }
        }
    }

    private static void addPawnMove(Board board, Square from, Square to,
                                     Piece piece, int promotionRank,
                                     List<Move> moves) {
        if (to.rank() == promotionRank) {
            for (PieceType pt : new PieceType[]{
                    PieceType.QUEEN, PieceType.ROOK,
                    PieceType.BISHOP, PieceType.KNIGHT}) {
                Move m = Move.promotion(from, to,
                    board.pieceAt(to).orElse(null), pt);
                if (MoveValidator.isLegal(board, m)) moves.add(m);
            }
        } else {
            tryAdd(board, from, to, piece, moves);
        }
    }

    /**
     * Builds a candidate Move and adds it if MoveValidator approves.
     * Automatically wraps capture moves with the captured piece.
     */
    private static void tryAdd(Board board, Square from, Square to,
                                Piece piece, List<Move> moves) {
        var dest = board.pieceAt(to);
        // Don't capture friendly pieces
        if (dest.isPresent() && dest.get().color() == piece.color()) return;

        Move m;
        if (piece.type() == PieceType.KING
                && Math.abs(to.file() - from.file()) == 2) {
            m = Move.castling(from, to);
        } else if (dest.isPresent()) {
            m = Move.capture(from, to, dest.get());
        } else {
            m = Move.normal(from, to);
        }

        if (MoveValidator.isLegal(board, m)) moves.add(m);
    }

    private static boolean inBounds(Square sq) {
        return sq.file() >= 0 && sq.file() < Board.SIZE
            && sq.rank() >= 0 && sq.rank() < Board.SIZE;
    }
}
