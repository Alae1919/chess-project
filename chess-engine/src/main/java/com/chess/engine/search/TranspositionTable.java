package com.chess.engine.search;

import com.chess.domain.model.Move;

import java.util.Arrays;

/**
 * Transposition table — caches position evaluations to skip re-searching identical positions.
 *
 * Implementation notes:
 *   - Flat parallel arrays (not Entry[] objects) for L1/L2 cache efficiency.
 *   - Always-replace eviction: simplest strategy, good for single-game searches.
 *   - Table size is a power of two so index = hash & mask (no modulo).
 *   - Default: 1 << 20 ≈ 1M entries (~48 MB with Move references).
 */
public final class TranspositionTable {

    public enum Flag { EXACT, LOWER_BOUND, UPPER_BOUND }

    /** A cache hit returned by {@link #probe(long)}. */
    public record Entry(int depth, int score, Flag flag, Move bestMove) {}

    private final long[] hashes;
    private final int[]  depths;
    private final int[]  scores;
    private final byte[] flags;      // Flag.ordinal() stored as byte
    private final Move[] bestMoves;
    private final int    mask;

    /** Default constructor: 1M entries (2^20). */
    public TranspositionTable() {
        this(20);
    }

    /**
     * @param sizePow2 log₂ of the number of entries (e.g., 20 → 1 048 576 entries).
     */
    public TranspositionTable(int sizePow2) {
        int size    = 1 << sizePow2;
        hashes      = new long[size];
        depths      = new int[size];
        scores      = new int[size];
        flags       = new byte[size];
        bestMoves   = new Move[size];
        mask        = size - 1;
    }

    /** Store a search result. Always replaces the existing entry at this index. */
    public void store(long hash, int depth, int score, Flag flag, Move bestMove) {
        int idx       = index(hash);
        hashes[idx]   = hash;
        depths[idx]   = depth;
        scores[idx]   = score;
        flags[idx]    = (byte) flag.ordinal();
        bestMoves[idx] = bestMove;
    }

    /**
     * Returns the cached entry for this hash, or {@code null} on a miss.
     * Always verify {@code entry.depth() >= requiredDepth} before using the score.
     */
    public Entry probe(long hash) {
        int idx = index(hash);
        if (hashes[idx] != hash) return null;
        return new Entry(depths[idx], scores[idx],
                         Flag.values()[flags[idx] & 0xFF], bestMoves[idx]);
    }

    /** Invalidates all entries (call between games). */
    public void clear() {
        Arrays.fill(hashes, 0L);
    }

    private int index(long hash) {
        return (int)(hash & mask);
    }
}
