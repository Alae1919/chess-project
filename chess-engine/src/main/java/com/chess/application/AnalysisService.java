package com.chess.application;

import com.chess.engine.core.Position;
import com.chess.engine.core.eval.Evaluators;
import com.chess.engine.core.search.SearchLimits;
import com.chess.engine.core.search.SearchResult;
import com.chess.engine.core.search.Searcher;
import com.chess.infrastructure.metrics.ChessMeters;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Evaluates a position on request, outside any game's AI. A search is real CPU work, so it waits
 * for a free core with every other search (EngineCpuGate), and is turned away if none frees up in
 * a moment, rather than every request taking a thread and a table for itself.
 *
 * The same position gets the same answer: evaluations are kept by position, for every player (the
 * openings above all come up again and again), so most requests need no search at all.
 */
@Service
public class AnalysisService {

    /** The result of an analysis, from White's point of view. */
    public record Analysis(int scoreCp, int depth, String bestMove, boolean mate) { }

    private static final Duration WAIT_FOR_A_CORE = Duration.ofSeconds(2);
    private static final long THINK_MS = 400;

    private final EngineCpuGate gate;
    /** By position (see {@link #key}); a few MB at most */
    private final Cache<String, Analysis> cache = Caffeine.newBuilder()
        .maximumSize(50_000)
        .expireAfterAccess(Duration.ofHours(24))
        .recordStats()
        .build();

    @Autowired
    public AnalysisService(EngineCpuGate gate) {
        this.gate = gate;
        CaffeineCacheMetrics.monitor(Metrics.globalRegistry, cache, "analysis");
    }

    /** On its own, with two cores to search on (tests) */
    public AnalysisService() {
        this(new EngineCpuGate(2));
    }

    /** @throws IllegalStateException when the engine is too busy to take another position */
    public Analysis analyse(String fen) {
        long start = System.nanoTime();
        String outcome = "error";
        try {
            String key = key(fen);
            Analysis cached = cache.getIfPresent(key);
            if (cached != null) {
                outcome = "cached";
                return cached;
            }
            Analysis analysis = gate.tryRun("analysis", WAIT_FOR_A_CORE, () -> search(fen));
            cache.put(key, analysis);
            outcome = "ok";
            return analysis;
        } catch (IllegalStateException e) {
            outcome = "busy";
            throw e;
        } finally {
            ChessMeters.timer("chess.analysis", "An evaluation asked for by a player, waiting for a core included",
                    "outcome", outcome).record(Duration.ofNanos(System.nanoTime() - start));
        }
    }

    /**
     * What makes two positions the same for the engine: the pieces, the side to move, castling and en passant (the
     * first four fields of the FEN). The move counters are left out: they do not change the evaluation here.
     */
    static String key(String fen) {
        String[] f = fen.trim().split("\\s+");
        return f.length >= 4 ? String.join(" ", f[0], f[1], f[2], f[3]) : fen.trim();
    }

    private Analysis search(String fen) {
        Position pos = Position.fromFen(fen);
        Searcher searcher = new Searcher(Evaluators.create(), 4);
        SearchResult result = searcher.search(pos, SearchLimits.moveTime(THINK_MS));
        int score = pos.sideToMove() == Position.WHITE ? result.score() : -result.score();
        return new Analysis(score, result.depth(), result.bestMove() == 0 ? null : result.bestMoveUci(), result.isMate());
    }
}
