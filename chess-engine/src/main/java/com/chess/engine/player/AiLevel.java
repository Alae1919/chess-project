package com.chess.engine.player;

import com.chess.engine.core.search.SearchLimits;
import com.chess.engine.core.search.SearchResult;

import java.util.List;
import java.util.random.RandomGenerator;

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
 * The numbers come from calibrating each level against Stockfish held back to a chosen Elo (about 1100,
 * 1400, 1750, 2050, 2550 and 2900 on Stockfish's scale; see docs/how-the-ai-works.md).
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

    /**
     * What to search for this level, in a game with the given clock for the side to move
     * ({@code remainingMs} of 0 means no clock). On a clock the AI never thinks longer than the
     * game can spare, whatever the level.
     */
    public SearchLimits limits(long remainingMs, long incrementMs) {
        long moveTime = moveTimeMs;
        if (remainingMs > 0) {
            long spare = Math.max(20, Math.min(remainingMs / 30 + incrementMs * 3 / 4, remainingMs / 4));
            moveTime = moveTime > 0 ? Math.min(moveTime, spare) : spare;
        }
        return new SearchLimits(maxDepth, maxNodes, moveTime, 0, 0, 0, 0, 0, multiPv);
    }

    /**
     * The strongest levels take the best move. The others pick among the candidates in
     * proportion to exp(-gap / temperature), so a move a little worse than the best is quite
     * likely and one that loses material outright almost never is. A forced mate is always played.
     */
    public int choose(SearchResult result, RandomGenerator random) {
        List<SearchResult.Line> lines = result.lines();
        if (lines.size() < 2 || temperatureCp <= 0 || result.isMate()) return result.bestMove();

        int best = lines.get(0).score();
        double[] weight = new double[lines.size()];
        double total = 0;
        for (int i = 0; i < lines.size(); i++) {
            weight[i] = Math.exp(-(best - lines.get(i).score()) / (double) temperatureCp);
            total += weight[i];
        }
        double ticket = random.nextDouble() * total;
        for (int i = 0; i < weight.length; i++) {
            ticket -= weight[i];
            if (ticket <= 0) return lines.get(i).move();
        }
        return lines.get(0).move();
    }

    public static AiLevel of(int level) {
        return switch (Math.max(EASIEST, Math.min(HARDEST, level))) {
            case 1 -> new AiLevel(1, 2, 400, 0, 5, 160, 1);
            case 2 -> new AiLevel(2, 3, 1_500, 0, 4, 100, 1);
            case 3 -> new AiLevel(3, 5, 8_000, 0, 3, 50, 2);
            case 4 -> new AiLevel(4, 0, 5_000, 0, 1, 0, 4);
            case 5 -> new AiLevel(5, 0, 18_000, 0, 1, 0, 8);
            default -> new AiLevel(6, 0, 0, 2_000, 1, 0, 16);
        };
    }
}
