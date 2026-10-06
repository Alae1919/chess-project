package com.chess.application;

import com.chess.engine.core.Position;
import com.chess.engine.core.eval.Evaluators;
import com.chess.engine.core.search.SearchLimits;
import com.chess.engine.core.search.SearchResult;
import com.chess.engine.core.search.Searcher;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Evaluates a position on request, outside any game's AI. A search is real CPU work, so only
 * a couple run at once and the rest wait a moment or are turned away, rather than every
 * request taking a thread and a table for itself.
 */
@Service
public class AnalysisService {

    /** The result of an analysis, from White's point of view. */
    public record Analysis(int scoreCp, int depth, String bestMove, boolean mate) { }

    private static final int CONCURRENT_SEARCHES = 2;
    private static final long WAIT_FOR_A_SLOT_MS = 2_000;
    private static final long THINK_MS = 400;

    private final Semaphore slots = new Semaphore(CONCURRENT_SEARCHES);

    /** @throws IllegalStateException when the engine is too busy to take another position */
    public Analysis analyse(String fen) {
        Position pos = Position.fromFen(fen);
        boolean got;
        try {
            got = slots.tryAcquire(WAIT_FOR_A_SLOT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the engine");
        }
        if (!got) throw new IllegalStateException("The engine is busy; try again in a moment");
        try {
            Searcher searcher = new Searcher(Evaluators.create(), 4);
            SearchResult result = searcher.search(pos, SearchLimits.moveTime(THINK_MS));
            int score = pos.sideToMove() == Position.WHITE ? result.score() : -result.score();
            return new Analysis(score, result.depth(), result.bestMove() == 0 ? null : result.bestMoveUci(), result.isMate());
        } finally {
            slots.release();
        }
    }
}
