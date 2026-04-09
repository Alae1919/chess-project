package com.chess.domain.board;

import com.chess.domain.model.*;

/**
 * Converts between FEN strings and Board objects.
 *
 * T1 — Strict validation added:
 *   • Exactly 8 ranks separated by '/'.
 *   • Each rank expands to exactly 8 squares.
 *   • Only legal FEN characters are accepted (RNBQKPrnbqkp, digits 1-8).
 *   • Exactly one white king and one black king must be present.
 *
 * All violations throw {@link InvalidFenException} with a descriptive message.
 */
public final class FenParser {

    private FenParser() {}

    // ---- Public API ----------------------------------------------------

    public static Board parse(String fen) {
        if (fen == null || fen.isBlank())
            throw new InvalidFenException("FEN string is null or blank");

        String[] parts = fen.trim().split("\\s+");
        Piece[][] grid = parsePlacement(parts[0]);

        validateKings(grid);

        Color activeColor = parts.length > 1 && parts[1].equals("b")
            ? Color.BLACK : Color.WHITE;

        CastlingRights castling = parts.length > 2
            ? CastlingRights.fromString(parts[2]) : CastlingRights.all();

        Square epTarget = parts.length > 3 && !parts[3].equals("-")
            ? parseEpSquare(parts[3]) : null;

        int halfMove = parts.length > 4 ? parseNonNegativeInt(parts[4], "half-move clock") : 0;
        int fullMove = parts.length > 5 ? parsePositiveInt(parts[5], "full-move number") : 1;

        return new Board(grid, activeColor, castling, epTarget, halfMove, fullMove);
    }

    public static String toFen(Board board) {
        StringBuilder sb = new StringBuilder();
        // Rank 8 (index 7) first, down to rank 1 (index 0)
        for (int rank = Board.SIZE - 1; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < Board.SIZE; file++) {
                var opt = board.pieceAt(new Square(file, rank));
                if (opt.isEmpty()) {
                    empty++;
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    sb.append(fenChar(opt.get()));
                }
            }
            if (empty > 0) sb.append(empty);
            if (rank > 0) sb.append('/');
        }
        sb.append(' ').append(board.activeColor() == Color.WHITE ? 'w' : 'b');
        sb.append(' ').append(board.castlingRights());
        sb.append(' ').append(board.enPassantTarget().map(Square::toString).orElse("-"));
        sb.append(' ').append(board.halfMoveClock());
        sb.append(' ').append(board.fullMoveNumber());
        return sb.toString();
    }

    // ---- Piece placement -----------------------------------------------

    /**
     * Parses the piece-placement field (first FEN token).
     * Validates: exactly 8 ranks, each rank = 8 squares, legal chars only.
     */
    private static Piece[][] parsePlacement(String placement) {
        String[] ranks = placement.split("/", -1);  // -1 keeps trailing empties

        if (ranks.length != Board.SIZE)
            throw new InvalidFenException(
                "FEN must have exactly 8 ranks separated by '/', found: "
                + ranks.length + " in \"" + placement + "\"");

        Piece[][] grid = new Piece[Board.SIZE][Board.SIZE];

        // FEN rank order: first token = rank 8 (board index 7), last = rank 1 (index 0)
        for (int i = 0; i < ranks.length; i++) {
            int boardRank = Board.SIZE - 1 - i;   // rank 8 → index 7
            parseRank(ranks[i], boardRank, grid, i + 1);
        }
        return grid;
    }

    private static void parseRank(String rankStr, int boardRank,
                                   Piece[][] grid, int fenRankNumber) {
        int file = 0;
        for (int ci = 0; ci < rankStr.length(); ci++) {
            char c = rankStr.charAt(ci);

            if (Character.isDigit(c)) {
                int skip = c - '0';
                if (skip < 1 || skip > 8)
                    throw new InvalidFenException(
                        "Illegal digit '" + c + "' in FEN rank " + fenRankNumber
                        + " (must be 1-8)");
                file += skip;
                if (file > Board.SIZE)
                    throw new InvalidFenException(
                        "FEN rank " + fenRankNumber + " is wider than 8 squares");
            } else if (isLegalFenChar(c)) {
                if (file >= Board.SIZE)
                    throw new InvalidFenException(
                        "FEN rank " + fenRankNumber + " is wider than 8 squares");
                grid[file][boardRank] = pieceFromFen(c);
                file++;
            } else {
                throw new InvalidFenException(
                    "Illegal character '" + c + "' in FEN rank " + fenRankNumber);
            }
        }

        if (file != Board.SIZE)
            throw new InvalidFenException(
                "FEN rank " + fenRankNumber + " has " + file
                + " squares instead of 8");
    }

    // ---- King validation -----------------------------------------------

    /**
     * Verifies exactly one white king and one black king are present.
     * Missing or duplicate kings both throw InvalidFenException.
     */
    private static void validateKings(Piece[][] grid) {
        int whiteKings = 0, blackKings = 0;
        for (int f = 0; f < Board.SIZE; f++) {
            for (int r = 0; r < Board.SIZE; r++) {
                Piece p = grid[f][r];
                if (p == null || p.type() != PieceType.KING) continue;
                if (p.color() == Color.WHITE) whiteKings++;
                else                          blackKings++;
            }
        }
        if (whiteKings == 0)
            throw new InvalidFenException("No white king (K) found in FEN");
        if (whiteKings > 1)
            throw new InvalidFenException(
                "Too many white kings (" + whiteKings + ") in FEN");
        if (blackKings == 0)
            throw new InvalidFenException("No black king (k) found in FEN");
        if (blackKings > 1)
            throw new InvalidFenException(
                "Too many black kings (" + blackKings + ") in FEN");
    }

    // ---- Helpers -------------------------------------------------------

    private static boolean isLegalFenChar(char c) {
        return "RNBQKPrnbqkp".indexOf(c) >= 0;
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
            default  -> throw new InvalidFenException("Unknown FEN char: " + c);
        };
        return Piece.of(color, type);
    }

    private static char fenChar(Piece p) {
        char c = p.type().fenChar();
        return p.color() == Color.WHITE ? c : Character.toLowerCase(c);
    }

    private static Square parseEpSquare(String s) {
        try {
            return Square.of(s);
        } catch (IllegalArgumentException e) {
            throw new InvalidFenException("Invalid en-passant square: " + s);
        }
    }

    private static int parseNonNegativeInt(String s, String field) {
        try {
            int v = Integer.parseInt(s);
            if (v < 0) throw new InvalidFenException(field + " must be >= 0, got: " + v);
            return v;
        } catch (NumberFormatException e) {
            throw new InvalidFenException("Invalid " + field + ": " + s);
        }
    }

    private static int parsePositiveInt(String s, String field) {
        try {
            int v = Integer.parseInt(s);
            if (v < 1) throw new InvalidFenException(field + " must be >= 1, got: " + v);
            return v;
        } catch (NumberFormatException e) {
            throw new InvalidFenException("Invalid " + field + ": " + s);
        }
    }
}
