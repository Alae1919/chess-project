package com.chess.engine.core.search;

/** Progress report after each completed iteration, for UCI "info" lines and logging. */
public record SearchInfo(int depth, int multiPvIndex, int score, long nodes, long timeMs, int hashFullPermille, int[] pv) {

    public boolean isMate() { return Math.abs(score) >= Searcher.MATE_IN_MAX; }

    public int mateIn() {
        int moves = (Searcher.MATE - Math.abs(score) + 1) / 2;
        return score > 0 ? moves : -moves;
    }

    public long nodesPerSecond() { return timeMs <= 0 ? 0 : nodes * 1000 / timeMs; }
}
