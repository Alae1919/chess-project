package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;

/**
 * Evaluates the current game state for the given color.
 *
 * States (in evaluation priority order):
 *   CHECKMATE    — in check AND no legal moves. Wins even on the move that
 *                  would also reach the 50-move limit.
 *   STALEMATE    — not in check AND no legal moves.
 *   DRAW_INSUFFICIENT_MATERIAL — neither side can ever mate (see InsufficientMaterial).
 *   DRAW_50_MOVE — half-move clock has reached 100 (50 full moves without
 *                  pawn move or capture).
 *   CHECK        — in check but has at least one legal escape.
 *   ONGOING      — normal position.
 *
 *   Session-only outcomes (not produced by evaluate(), set on GameSession,
 *   which owns the move history these need):
 *   WHITE_RESIGNED, BLACK_RESIGNED, DRAW_AGREED, DRAW_REPETITION,
 *   WHITE_FLAGGED, BLACK_FLAGGED (that side ran out of time),
 *   WHITE_ABANDONED, BLACK_ABANDONED (that side left an online game) — game is over.
 */
public final class GameStateChecker {

    public enum State {
        ONGOING, CHECK, CHECKMATE, STALEMATE, DRAW_50_MOVE, DRAW_INSUFFICIENT_MATERIAL,
        WHITE_RESIGNED, BLACK_RESIGNED, DRAW_AGREED, DRAW_REPETITION,
        WHITE_FLAGGED, BLACK_FLAGGED, WHITE_ABANDONED, BLACK_ABANDONED
    }

    private GameStateChecker() {}

    public static State evaluate(Board board, Color color) {
        boolean inCheck = CheckDetector.isInCheck(board, color);
        boolean hasMove = !MoveGenerator.generateLegalMoves(board, color).isEmpty();

        // Mate and stalemate come first: they end the game even when a draw rule also applies
        if (!hasMove) return inCheck ? State.CHECKMATE : State.STALEMATE;

        if (InsufficientMaterial.isDead(board)) return State.DRAW_INSUFFICIENT_MATERIAL;
        if (board.halfMoveClock() >= 100)       return State.DRAW_50_MOVE;

        return inCheck ? State.CHECK : State.ONGOING;
    }

    public static boolean isTerminal(State state) {
        return state == State.CHECKMATE
            || state == State.STALEMATE
            || state == State.DRAW_50_MOVE
            || state == State.DRAW_INSUFFICIENT_MATERIAL
            || state == State.WHITE_RESIGNED
            || state == State.BLACK_RESIGNED
            || state == State.DRAW_AGREED
            || state == State.DRAW_REPETITION
            || state == State.WHITE_FLAGGED
            || state == State.BLACK_FLAGGED
            || state == State.WHITE_ABANDONED
            || state == State.BLACK_ABANDONED;
    }
}
