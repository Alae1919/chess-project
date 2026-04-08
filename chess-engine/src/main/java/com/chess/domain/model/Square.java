package com.chess.domain.model;

/**
 * An immutable board coordinate.
 *
 * file: 0 = a-file … 7 = h-file
 * rank: 0 = rank 1 … 7 = rank 8
 *
 * Replaces the original Position class and standardises naming.
 */
public record Square(int file, int rank) {

    public Square {
        if (file < 0 || file > 7 || rank < 0 || rank > 7)
            throw new IllegalArgumentException(
                "Invalid square: file=" + file + " rank=" + rank);
    }

    /** Parse standard algebraic notation, e.g. "e4" → Square(4, 3). */
    public static Square of(String algebraic) {
        if (algebraic == null || algebraic.length() != 2)
            throw new IllegalArgumentException("Expected algebraic notation like 'e4'");
        int file = algebraic.charAt(0) - 'a';
        int rank = algebraic.charAt(1) - '1';
        return new Square(file, rank);
    }

    /** Build from file letter + rank digit, e.g. 'e', 4. */
    public static Square of(char fileLetter, int rankNumber) {
        return new Square(fileLetter - 'a', rankNumber - 1);
    }

    /** Standard algebraic notation output, e.g. "e4". */
    @Override
    public String toString() {
        return "" + (char)('a' + file) + (rank + 1);
    }
}
