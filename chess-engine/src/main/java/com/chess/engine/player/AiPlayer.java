package com.chess.engine.player;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;
import com.chess.domain.model.Move;
import com.chess.engine.opening.OpeningBook;
import com.chess.engine.search.AlphaBetaSearch;
import com.chess.engine.search.SearchConfig;

/**
 * AI player backed by alpha-beta search with an optional opening book.
 *
 * Move selection priority:
 *   1. Opening book lookup (if the book is loaded and the position is known)
 *   2. Alpha-beta search
 *
 * The {@link AlphaBetaSearch} instance is kept for the lifetime of this player so
 * that the transposition table accumulates knowledge across all moves of the game.
 *
 * chooseMove() throws IllegalStateException if called on a terminal position
 * (the GameService must check GameStateChecker before calling the player).
 */
public final class AiPlayer implements Player {

    private static final OpeningBook BOOK = OpeningBook.load();

    private final Color           color;
    private final SearchConfig    config;
    private final AlphaBetaSearch search;

    public AiPlayer(Color color)            { this(color, 4); }
    public AiPlayer(Color color, int depth) { this(color, SearchConfig.fixedDepth(depth)); }

    public AiPlayer(Color color, SearchConfig config) {
        this.color  = color;
        this.config = config;
        this.search = new AlphaBetaSearch();
    }

    @Override
    public Move chooseMove(Board board) {
        // 1. Try the opening book first
        if (BOOK.isAvailable()) {
            var bookMove = BOOK.lookup(board);
            if (bookMove.isPresent()) return bookMove.get();
        }

        // 2. Fall back to alpha-beta search
        return search.findBestMove(board, config)
            .orElseThrow(() -> new IllegalStateException(
                "chooseMove() called on a terminal position for " + color));
    }

    @Override
    public Color color() { return color; }
}
