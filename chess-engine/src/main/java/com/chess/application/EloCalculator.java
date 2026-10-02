package com.chess.application;

/**
 * Standard Elo rating arithmetic. A game moves points from the loser to the
 * winner, so both players' changes always cancel out (except when a rating hits the floor).
 */
public final class EloCalculator {

    /** How far one game can move a rating. */
    public static final int K_FACTOR = 32;
    /** Ratings never drop below this. */
    public static final int FLOOR = 100;

    private EloCalculator() {}

    /** Expected score of a player rated {@code rating} against {@code opponent}: 0..1. */
    public static double expectedScore(int rating, int opponent) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponent - rating) / 400.0));
    }

    /**
     * Rating change for the first player.
     *
     * @param score 1 for a win, 0.5 for a draw, 0 for a loss
     */
    public static int delta(int rating, int opponent, double score) {
        return (int) Math.round(K_FACTOR * (score - expectedScore(rating, opponent)));
    }

    /** {@code rating + delta}, kept at or above {@link #FLOOR}. */
    public static int apply(int rating, int delta) {
        return Math.max(FLOOR, rating + delta);
    }
}
