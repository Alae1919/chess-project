package com.chess.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Square")
class SquareTest {

    @Test
    @DisplayName("parses algebraic notation correctly")
    void parseAlgebraic() {
        Square e4 = Square.of("e4");
        assertEquals(4, e4.file());
        assertEquals(3, e4.rank());
    }

    @Test
    @DisplayName("toString returns algebraic notation")
    void toStringIsAlgebraic() {
        assertEquals("e4", new Square(4, 3).toString());
        assertEquals("a1", new Square(0, 0).toString());
        assertEquals("h8", new Square(7, 7).toString());
    }

    @Test
    @DisplayName("rejects out-of-bounds coordinates")
    void rejectsInvalidCoords() {
        assertThrows(IllegalArgumentException.class, () -> new Square(8, 0));
        assertThrows(IllegalArgumentException.class, () -> new Square(0, -1));
    }
}
