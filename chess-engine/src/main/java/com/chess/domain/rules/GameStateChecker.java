package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;

/**
 * Evaluates the current game state for the given color.
 *
 * States (in evaluation priority order):
 *   DRAW_50_MOVE — half-move clock has reached 100 (50 full moves without
 *                  pawn move or capture). Checked BEFORE move generation
 *                  because it applies even when the player has legal moves.
 *   CHECKMATE    — in check AND no legal moves.
 *   STALEMATE    — not in check AND no legal moves.
 *   CHECK        — in check but has at least one legal escape.
 *   ONGOING      — normal position.
 *
 *   Session-only outcomes (not produced by evaluate(), set on GameSession):
 *   WHITE_RESIGNED, BLACK_RESIGNED, DRAW_AGREED — game is over.
 */
public final class GameStateChecker {

    public enum State {
        ONGOING, CHECK, CHECKMATE, STALEMATE, DRAW_50_MOVE,
        WHITE_RESIGNED, BLACK_RESIGNED, DRAW_AGREED
    }

    private GameStateChecker() {}

    public static State evaluate(Board board, Color color) {
        // 50-move rule applies regardless of whether moves are available
        if (board.halfMoveClock() >= 100) return State.DRAW_50_MOVE;

        boolean inCheck = CheckDetector.isInCheck(board, color);
        boolean hasMove = !MoveGenerator.generateLegalMoves(board, color).isEmpty();

        if (hasMove)  return inCheck ? State.CHECK : State.ONGOING;
        if (inCheck)  return State.CHECKMATE;
        return State.STALEMATE;
    }

    public static boolean isTerminal(State state) {
        return state == State.CHECKMATE
            || state == State.STALEMATE
            || state == State.DRAW_50_MOVE
            || state == State.WHITE_RESIGNED
            || state == State.BLACK_RESIGNED
            || state == State.DRAW_AGREED;
    }
}
