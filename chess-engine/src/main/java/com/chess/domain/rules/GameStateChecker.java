package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;

/**
 * Evaluates whether the game has ended and how.
 */
public final class GameStateChecker {

    public enum State { ONGOING, CHECK, CHECKMATE, STALEMATE, DRAW_50_MOVE }

    private GameStateChecker() {}

    public static State evaluate(Board board, Color color) {
        boolean inCheck  = CheckDetector.isInCheck(board, color);
        boolean hasMove  = !MoveGenerator.generateLegalMoves(board, color).isEmpty();

        if (hasMove)  return inCheck ? State.CHECK : State.ONGOING;
        if (inCheck)  return State.CHECKMATE;
        if (board.halfMoveClock() >= 100) return State.DRAW_50_MOVE;
        return State.STALEMATE;
    }

    public static boolean isTerminal(State state) {
        return state == State.CHECKMATE || state == State.STALEMATE
            || state == State.DRAW_50_MOVE;
    }
}
