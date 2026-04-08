package com.chess.shared.port;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;
import com.chess.domain.model.Move;
import com.chess.domain.rules.GameStateChecker.State;

/** Output port: the application layer writes here; adapters implement it. */
public interface OutputPort {
    void displayBoard(Board board);
    void displayMove(Move move);
    void displayCheck(Color colorInCheck);
    void displayResult(State state, Color loser);
}
