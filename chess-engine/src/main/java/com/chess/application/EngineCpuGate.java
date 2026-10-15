package com.chess.application;

import com.chess.infrastructure.metrics.ChessMeters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * At most one engine search per core, across every game and every analysis. A search uses a core
 * flat out for as long as it runs (two seconds at level 6), and the server has two: without a
 * limit, a few searches at once starved the requests, the database and each other. Those over the
 * limit wait their turn, first come first served, and the wait is timed
 * ({@code chess.engine.wait}, by {@code kind}).
 */
@Component
public class EngineCpuGate {

    private final Semaphore slots;

    public EngineCpuGate() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    EngineCpuGate(int slots) {
        this.slots = new Semaphore(slots, true);
    }

    /** Runs `search` once a core is free, waiting as long as it takes (an AI move must be played). */
    public <T> T run(String kind, Supplier<T> search) {
        long start = System.nanoTime();
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the engine");
        }
        return runHolding(kind, start, search);
    }

    /**
     * Runs `search` if a core is free within `maxWait`.
     *
     * @throws IllegalStateException when none is: the engine is too busy, try again in a moment
     */
    public <T> T tryRun(String kind, Duration maxWait, Supplier<T> search) {
        long start = System.nanoTime();
        boolean got;
        try {
            got = slots.tryAcquire(maxWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the engine");
        }
        if (!got) throw new IllegalStateException("The engine is busy; try again in a moment");
        return runHolding(kind, start, search);
    }

    private <T> T runHolding(String kind, long waitStart, Supplier<T> search) {
        ChessMeters.timer("chess.engine.wait", "Time a search waited for a free core", "kind", kind)
            .record(Duration.ofNanos(System.nanoTime() - waitStart));
        try {
            return search.get();
        } finally {
            slots.release();
        }
    }
}
