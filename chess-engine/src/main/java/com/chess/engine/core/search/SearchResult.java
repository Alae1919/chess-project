package com.chess.engine.core.search;

import com.chess.engine.core.Move;

import java.util.List;

/**
 * What a search found.
 *
 * @param bestMove the move to play, or {@link Move#NONE} when the position has no legal move
 * @param score    centipawns for the side to move; near +-{@link Searcher#MATE} means mate
 * @param lines    the best lines, best first (one unless MultiPV was asked for)
 */
public record SearchResult(int bestMove, int score, int depth, long nodes, long timeMs, int[] pv, List<Line> lines) {

    /** One root move with its score and the line that follows it. */
    public record Line(int move, int score, int[] pv) { }

    public boolean isMate() { return Math.abs(score) >= Searcher.MATE_IN_MAX; }

    /** Moves until mate: positive when the side to move mates, negative when it is mated. */
    public int mateIn() {
        int plies = Searcher.MATE - Math.abs(score);
        int moves = (plies + 1) / 2;
        return score > 0 ? moves : -moves;
    }

    public String bestMoveUci() { return Move.toUci(bestMove); }
}
