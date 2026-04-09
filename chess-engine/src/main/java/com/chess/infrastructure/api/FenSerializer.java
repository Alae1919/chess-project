package com.chess.infrastructure.api;

import com.chess.domain.board.Board;
import com.chess.domain.board.FenParser;

/**
 * Infrastructure adapter that serialises a Board to a FEN string.
 * Kept here (not in domain) because FEN is an external representation,
 * and the domain Board should not know about serialization formats.
 *
 * Note: FenParser.toFen() already exists in the domain — this class
 * is just a named boundary that prevents the controller layer from
 * importing domain internals directly.
 */
public final class FenSerializer {
    private FenSerializer() {}

    public static String toFen(Board board) {
        return FenParser.toFen(board);
    }
}
