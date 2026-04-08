package com.chess.domain.board;

import com.chess.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FenParser")
class FenParserTest {

    private static final String START_FEN =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    @DisplayName("parses starting position correctly")
    void parseStartingPosition() {
        Board board = FenParser.parse(START_FEN);
        assertEquals(Color.WHITE, board.activeColor());
        // White king on e1
        var e1 = board.pieceAt(Square.of("e1"));
        assertTrue(e1.isPresent());
        assertEquals(PieceType.KING, e1.get().type());
        assertEquals(Color.WHITE, e1.get().color());
        // e4 is empty
        assertTrue(board.isEmpty(Square.of("e4")));
    }

    @Test
    @DisplayName("round-trip FEN serialization")
    void roundTrip() {
        Board board = FenParser.parse(START_FEN);
        assertEquals(START_FEN, FenParser.toFen(board));
    }

    @Test
    @DisplayName("parses custom position")
    void customPosition() {
        // Original test case from EchecTest: rooks and kings only
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/4K3 w - - 0 1");
        assertTrue(board.pieceAt(Square.of("e1")).isPresent());
        assertTrue(board.pieceAt(Square.of("e8")).isPresent());
    }
}
