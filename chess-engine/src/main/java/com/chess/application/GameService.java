package com.chess.application;

import com.chess.domain.board.Board;
import com.chess.domain.board.BoardFactory;
import com.chess.domain.model.Color;
import com.chess.domain.model.Move;
import com.chess.domain.rules.GameStateChecker;
import com.chess.domain.rules.GameStateChecker.State;
import com.chess.engine.player.Player;
import com.chess.shared.port.OutputPort;

/**
 * Use-case: play a full game of chess.
 *
 * Dependencies are injected, so this class is testable in isolation
 * and can serve any delivery mechanism (console, REST, WebSocket).
 */
public final class GameService {

    private final Player whitePlayer;
    private final Player blackPlayer;
    private final OutputPort output;

    public GameService(Player whitePlayer, Player blackPlayer, OutputPort output) {
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        this.output      = output;
    }

    /** Plays a game from the starting position to termination. */
    public void play() {
        play(BoardFactory.startingPosition());
    }

    /** Plays a game from a custom starting position (useful for tests/puzzles). */
    public void play(Board initialBoard) {
        Board board = initialBoard;
        output.displayBoard(board);

        while (true) {
            Color active = board.activeColor();
            State state  = GameStateChecker.evaluate(board, active);

            if (GameStateChecker.isTerminal(state)) {
                output.displayResult(state, active);
                break;
            }

            if (state == State.CHECK) {
                output.displayCheck(active);
            }

            Player current = active == Color.WHITE ? whitePlayer : blackPlayer;
            Move move = current.chooseMove(board);
            board = board.apply(move);
            output.displayMove(move);
            output.displayBoard(board);
        }
    }
}
