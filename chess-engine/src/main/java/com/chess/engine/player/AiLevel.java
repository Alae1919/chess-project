package com.chess.engine.player;

/**
 * How hard the AI plays at each of the six difficulty levels.
 *
 * Every level plays the same engine; a level only decides how much it may think and how
 * carefully it picks among its best candidate moves. The weaker levels search a few hundred
 * to a few thousand positions, look at several candidate moves, and choose among those that
 * are close in score with a probability that falls off with the gap (a "temperature"), so they
 * make the sort of inaccuracies a person does rather than the one-ply blunders a depth-limited
 * search makes. The stronger levels search for a fixed time and always take the best move.
 *
 * The numbers are a first calibration: see the Elo estimates measured in the engine README.
 *
 * @param level          1 (easiest) to 6 (strongest)
 * @param maxDepth       deepest iteration, 0 for no limit
 * @param maxNodes       positions to search, 0 for no limit
 * @param moveTimeMs     search time in milliseconds, 0 for no limit
 * @param multiPv        how many candidate moves to consider
 * @param temperatureCp  how readily a candidate worse than the best is chosen (0 = never)
 * @param hashMegabytes  transposition table size
 */
public record AiLevel(int level, int maxDepth, long maxNodes, long moveTimeMs, int multiPv, int temperatureCp, int hashMegabytes) {

    public static final int EASIEST = 1, HARDEST = 6, DEFAULT = 4;

    public static AiLevel of(int level) {
        return switch (Math.max(EASIEST, Math.min(HARDEST, level))) {
            case 1 -> new AiLevel(1, 2, 400, 0, 5, 160, 1);
            case 2 -> new AiLevel(2, 3, 1_500, 0, 4, 100, 1);
            case 3 -> new AiLevel(3, 5, 8_000, 0, 3, 50, 2);
            case 4 -> new AiLevel(4, 0, 0, 150, 1, 0, 4);
            case 5 -> new AiLevel(5, 0, 0, 600, 1, 0, 8);
            default -> new AiLevel(6, 0, 0, 2_000, 1, 0, 16);
        };
    }
}
