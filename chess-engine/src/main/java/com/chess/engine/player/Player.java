package com.chess.engine.player;

import com.chess.domain.board.Board;
import com.chess.domain.model.Move;

/** Strategy interface for choosing a move given a board position. */
public interface Player {
    Move chooseMove(Board board);
    com.chess.domain.model.Color color();
}
