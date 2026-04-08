package com.chess.engine.player;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;
import com.chess.domain.model.Move;
import com.chess.engine.search.AlphaBetaSearch;

/** AI player using iterative-deepening alpha-beta search. */
public final class AiPlayer implements Player {

    private final Color color;
    private final int depth;
    private final AlphaBetaSearch search;

    public AiPlayer(Color color) {
        this(color, 4);
    }

    public AiPlayer(Color color, int depth) {
        this.color  = color;
        this.depth  = depth;
        this.search = new AlphaBetaSearch();
    }

    @Override
    public Move chooseMove(Board board) {
        return search.findBestMove(board, depth);
    }

    @Override
    public Color color() { return color; }
}
