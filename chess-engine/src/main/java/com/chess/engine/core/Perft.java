package com.chess.engine.core;

/**
 * Counts the leaf positions reachable in exactly {@code depth} moves. The numbers for well-known
 * positions are published, so a match is strong evidence that move generation and make/unmake
 * are right, and the run time says how fast they are.
 */
public final class Perft {

    private Perft() {}

    public static long perft(Position pos, int depth) {
        int[][] buffers = new int[depth + 1][MoveGen.MAX_MOVES];
        return count(pos, depth, buffers);
    }

    private static long count(Position pos, int depth, int[][] buffers) {
        int[] moves = buffers[depth];
        int n = MoveGen.legal(pos, moves);
        if (depth == 1) return n;            // no need to play the last move to count it
        long total = 0;
        for (int i = 0; i < n; i++) {
            pos.makeMove(moves[i]);
            total += count(pos, depth - 1, buffers);
            pos.unmakeMove();
        }
        return total;
    }

    /** Leaves under each root move: the usual way to find which move a generator gets wrong. */
    public static java.util.Map<String, Long> divide(Position pos, int depth) {
        java.util.Map<String, Long> result = new java.util.TreeMap<>();
        int[][] buffers = new int[depth + 1][MoveGen.MAX_MOVES];
        int[] moves = new int[MoveGen.MAX_MOVES];
        int n = MoveGen.legal(pos, moves);
        for (int i = 0; i < n; i++) {
            pos.makeMove(moves[i]);
            result.put(Move.toUci(moves[i]), depth == 1 ? 1L : count(pos, depth - 1, buffers));
            pos.unmakeMove();
        }
        return result;
    }
}
