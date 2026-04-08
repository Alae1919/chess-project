package com.chess.domain.board;

import com.chess.domain.model.*;

/**
 * Converts between FEN strings and Board objects.
 *
 * Standard FEN: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
 *   - Piece placement: rank 8 first, rank 1 last
 *   - Active color: w or b
 *   - Castling: KQkq or subsets, or -
 *   - En-passant target square: e3 or -
 *   - Half-move clock
 *   - Full-move number
 */
public final class FenParser {

    private FenParser() {}

    public static Board parse(String fen) {
        String[] parts = fen.trim().split("\\s+");
        Piece[][] grid = parsePlacement(parts[0]);

        Color activeColor = parts.length > 1 && parts[1].equals("b")
            ? Color.BLACK : Color.WHITE;

        CastlingRights castling = parts.length > 2
            ? parseCastling(parts[2]) : CastlingRights.all();

        Square epTarget = parts.length > 3 && !parts[3].equals("-")
            ? Square.of(parts[3]) : null;

        int halfMove  = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        int fullMove  = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;

        return new Board(grid, activeColor, castling, epTarget, halfMove, fullMove);
    }

    public static String toFen(Board board) {
        StringBuilder sb = new StringBuilder();
        // Piece placement — rank 8 (index 7) down to rank 1 (index 0)
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < Board.SIZE; file++) {
                var piece = board.pieceAt(new Square(file, rank));
                if (piece.isEmpty()) {
                    empty++;
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    char c = piece.get().type().fenChar();
                    sb.append(piece.get().color() == Color.WHITE ? c
                                                                 : Character.toLowerCase(c));
                }
            }
            if (empty > 0) sb.append(empty);
            if (rank > 0) sb.append('/');
        }
        sb.append(' ');
        sb.append(board.activeColor() == Color.WHITE ? 'w' : 'b');
        sb.append(' ');
        sb.append(castlingToFen(board.castlingRights()));
        sb.append(' ');
        sb.append(board.enPassantTarget().map(Square::toString).orElse("-"));
        sb.append(' ');
        sb.append(board.halfMoveClock());
        sb.append(' ');
        sb.append(board.fullMoveNumber());
        return sb.toString();
    }

    // --- Private helpers -------------------------------------------------

    private static Piece[][] parsePlacement(String placement) {
        Piece[][] grid = new Piece[Board.SIZE][Board.SIZE];
        String[] ranks = placement.split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("FEN must contain 8 ranks");
        }
        // FEN rank 0 in the string = rank 8 on the board
        for (int i = 0; i < ranks.length; i++) {
            int rank = 7 - i;   // FEN starts from rank 8 (index 7)
            int file = 0;
            for (char c : ranks[i].toCharArray()) {
                if (Character.isDigit(c)) {
                    file += (c - '0');
                } else {
                    grid[file][rank] = pieceFromFen(c);
                    file++;
                }
            }
            if (file != 8) {
                throw new IllegalArgumentException("Invalid FEN rank: " + ranks[i]);
            }
        }
        return grid;
    }

    private static Piece pieceFromFen(char c) {
        Color color = Character.isUpperCase(c) ? Color.WHITE : Color.BLACK;
        PieceType type = switch (Character.toUpperCase(c)) {
            case 'P' -> PieceType.PAWN;
            case 'R' -> PieceType.ROOK;
            case 'N' -> PieceType.KNIGHT;
            case 'B' -> PieceType.BISHOP;
            case 'Q' -> PieceType.QUEEN;
            case 'K' -> PieceType.KING;
            default  -> throw new IllegalArgumentException("Unknown FEN char: " + c);
        };
        return Piece.of(color, type);
    }

    private static CastlingRights parseCastling(String s) {
        if (s.equals("-")) return CastlingRights.none();
        return new CastlingRights(
            s.contains("K"), s.contains("Q"),
            s.contains("k"), s.contains("q")
        );
    }

    private static String castlingToFen(CastlingRights r) {
        StringBuilder sb = new StringBuilder();
        if (r.whiteKingSide())  sb.append('K');
        if (r.whiteQueenSide()) sb.append('Q');
        if (r.blackKingSide())  sb.append('k');
        if (r.blackQueenSide()) sb.append('q');
        return sb.isEmpty() ? "-" : sb.toString();
    }
}
