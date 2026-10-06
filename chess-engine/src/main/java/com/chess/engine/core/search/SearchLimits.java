package com.chess.engine.core.search;

/**
 * When a search should stop. Any limit left at zero is not applied; with none at all the
 * search runs until it is stopped.
 *
 * @param depth        deepest iteration to run, in plies
 * @param nodes        stop after this many positions
 * @param moveTimeMs   spend exactly this long
 * @param whiteTimeMs  White's remaining clock, for the engine to budget from
 * @param blackTimeMs  Black's remaining clock
 * @param whiteIncMs   White's increment per move
 * @param blackIncMs   Black's increment per move
 * @param movesToGo    moves until the next time control, if the game has one
 * @param multiPv      how many best lines to find (1 = just the best move)
 */
public record SearchLimits(int depth, long nodes, long moveTimeMs,
                           long whiteTimeMs, long blackTimeMs, long whiteIncMs, long blackIncMs,
                           int movesToGo, int multiPv) {

    public static SearchLimits unlimited() { return new SearchLimits(0, 0, 0, 0, 0, 0, 0, 0, 1); }
    public static SearchLimits depth(int plies) { return new SearchLimits(plies, 0, 0, 0, 0, 0, 0, 0, 1); }
    public static SearchLimits nodes(long positions) { return new SearchLimits(0, positions, 0, 0, 0, 0, 0, 0, 1); }
    public static SearchLimits moveTime(long ms) { return new SearchLimits(0, 0, ms, 0, 0, 0, 0, 0, 1); }

    public SearchLimits withMultiPv(int lines) {
        return new SearchLimits(depth, nodes, moveTimeMs, whiteTimeMs, blackTimeMs, whiteIncMs, blackIncMs, movesToGo, Math.max(1, lines));
    }

    public SearchLimits withDepth(int plies) {
        return new SearchLimits(plies, nodes, moveTimeMs, whiteTimeMs, blackTimeMs, whiteIncMs, blackIncMs, movesToGo, multiPv);
    }

    public SearchLimits withNodes(long positions) {
        return new SearchLimits(depth, positions, moveTimeMs, whiteTimeMs, blackTimeMs, whiteIncMs, blackIncMs, movesToGo, multiPv);
    }
}
