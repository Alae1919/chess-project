package com.chess.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * The engine's analysis of the position the landing page tells its story with. Every figure is
 * the output of one real search, never a constant: see {@link com.chess.application.ShowcaseService}.
 * Scores are from White's point of view, as in {@link EvaluationDto}.
 */
public final class ShowcaseDto {

    private ShowcaseDto() {}

    /** A move in both notations: UCI for the board, SAN for people. */
    public record MoveRef(String uci, String san) {}

    /**
     * A score from White's point of view: centipawns, or moves to mate (positive when White
     * mates, negative when Black does). Exactly one of the two is set.
     */
    public record Score(Integer cp, Integer mateIn) {}

    /** One of the replies the engine weighed, with its score and the line it expects to follow (SAN). */
    public record Line(MoveRef move, Score score, List<String> pv) {}

    /** Where the search stood when it finished one depth: the totals so far and its choice at that depth. */
    public record Iteration(int depth, long nodes, long timeMs, MoveRef bestMove, Score score) {}

    public record Search(
        MoveRef bestMove,
        Score   score,
        int     depth,
        long    nodes,           // positions the search visited
        long    timeMs,          // how long it took on this server
        long    nodesPerSecond,
        List<Line> lines,        // the best replies, best first
        List<Iteration> iterations   // one per completed depth, shallowest first
    ) {}

    /** The tempting reply the story is about, and what the engine sees behind it. */
    public record Trap(MoveRef move, Score score, List<String> refutation, int depth, long nodes) {}

    public record Showcase(
        String  title,           // where the position comes from
        String  startFen,        // before the player's move
        MoveRef playerMove,      // the move "you" see
        String  fen,             // after it: the position the engine searched
        Search  engine,
        String  replyFen,        // after the engine's reply
        Trap    trap,
        String  evaluator,       // which evaluation the search used
        Instant analysedAt
    ) {}
}
