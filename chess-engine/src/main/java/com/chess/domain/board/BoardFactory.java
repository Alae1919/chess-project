package com.chess.domain.board;

import com.chess.domain.model.*;

/**
 * Creates Board instances for standard starting positions and from FEN strings.
 */
public final class BoardFactory {

    private BoardFactory() {}

    /** Standard chess starting position. */
    public static Board startingPosition() {
        return FenParser.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    /** Parse a custom FEN string, refusing positions that could never arise in a game. */
    public static Board fromFen(String fen) {
        return FenParser.parseStrict(fen);
    }
}
