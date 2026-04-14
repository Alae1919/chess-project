package com.chess.engine.search;

/**
 * Configuration for {@link AlphaBetaSearch}.
 *
 * Two common presets:
 *   {@link #fixedDepth(int)}  — deterministic, backward-compatible with old int-depth API
 *   {@link #timed(long)}      — iterative deepening until the time budget is exhausted
 *                               (recommended for production: always uses available time)
 */
public record SearchConfig(int maxDepth, long timeLimitMs) {

    /** Search to exactly {@code depth} plies with no time limit. */
    public static SearchConfig fixedDepth(int depth) {
        return new SearchConfig(depth, 0L);
    }

    /** Iterative deepening up to depth 99, stopping when {@code ms} milliseconds elapse. */
    public static SearchConfig timed(long ms) {
        return new SearchConfig(99, ms);
    }

    public boolean hasTimeLimit() {
        return timeLimitMs > 0;
    }
}
