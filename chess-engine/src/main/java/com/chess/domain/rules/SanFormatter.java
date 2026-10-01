package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.model.Move;
import com.chess.domain.model.Piece;
import com.chess.domain.model.PieceType;

import java.util.List;

/**
 * Formats a move in Standard Algebraic Notation (e.g. e4, Nbd7, exd5, O-O, e8=Q+, Qh5#).
 */
public final class SanFormatter {

    private SanFormatter() {}

    /**
     * @param before board before the move is played
     * @param move   a legal move on {@code before}
     * @param after  game state after the move (supplies the check / mate suffix)
     */
    public static String format(Board before, Move move, GameStateChecker.State after) {
        StringBuilder sb = new StringBuilder();
        Piece piece = before.pieceAt(move.from()).orElseThrow();

        if (move.isCastling()) {
            sb.append(move.to().file() > move.from().file() ? "O-O" : "O-O-O");
        } else {
            boolean capture = move.isCapture() || move.isEnPassant();
            if (piece.type() == PieceType.PAWN) {
                if (capture) sb.append((char) ('a' + move.from().file())).append('x');
            } else {
                sb.append(piece.type().fenChar());
                sb.append(disambiguation(before, move, piece));
                if (capture) sb.append('x');
            }
            sb.append(move.to());
            if (move.isPromotion()) sb.append('=').append(move.promotion().fenChar());
        }

        if (after == GameStateChecker.State.CHECKMATE) sb.append('#');
        else if (after == GameStateChecker.State.CHECK) sb.append('+');
        return sb.toString();
    }

    private static String disambiguation(Board before, Move move, Piece piece) {
        if (piece.type() == PieceType.KING) return "";
        List<Move> rivals = MoveGenerator.generateLegalMoves(before).stream()
            .filter(m -> !m.from().equals(move.from()))
            .filter(m -> m.to().equals(move.to()))
            .filter(m -> before.pieceAt(m.from()).map(p -> p.type() == piece.type()).orElse(false))
            .toList();
        if (rivals.isEmpty()) return "";

        boolean sameFile = rivals.stream().anyMatch(m -> m.from().file() == move.from().file());
        boolean sameRank = rivals.stream().anyMatch(m -> m.from().rank() == move.from().rank());
        String file = String.valueOf((char) ('a' + move.from().file()));
        String rank = String.valueOf(move.from().rank() + 1);
        if (!sameFile) return file;
        if (!sameRank) return rank;
        return file + rank;
    }
}
