package com.chess.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PieceType geometry")
class PieceTypeTest {

    @Nested
    @DisplayName("Rook")
    class RookTests {
        @Test void movesHorizontally() {
            assertTrue(PieceType.ROOK.canReach(new Square(1,1), new Square(7,1)));
        }
        @Test void movesVertically() {
            assertTrue(PieceType.ROOK.canReach(new Square(1,1), new Square(1,6)));
        }
        @Test void doesNotMoveDiagonally() {
            assertFalse(PieceType.ROOK.canReach(new Square(1,1), new Square(2,2)));
        }
        @Test void doesNotMoveToSameSquare() {
            assertFalse(PieceType.ROOK.canReach(new Square(1,1), new Square(1,1)));
        }
    }

    @Nested
    @DisplayName("Bishop")
    class BishopTests {
        @Test void movesDiagonally() {
            assertTrue(PieceType.BISHOP.canReach(new Square(0,0), new Square(7,7)));
        }
        @Test void doesNotMoveHorizontally() {
            assertFalse(PieceType.BISHOP.canReach(new Square(0,0), new Square(7,0)));
        }
    }

    @Nested
    @DisplayName("Knight")
    class KnightTests {
        @Test void movesInLShape() {
            assertTrue(PieceType.KNIGHT.canReach(new Square(4,4), new Square(6,5)));
            assertTrue(PieceType.KNIGHT.canReach(new Square(4,4), new Square(2,3)));
        }
        @Test void doesNotMoveOneSquare() {
            assertFalse(PieceType.KNIGHT.canReach(new Square(4,4), new Square(5,4)));
        }
    }

    @Nested
    @DisplayName("Queen")
    class QueenTests {
        @Test void movesLikeRookAndBishop() {
            assertTrue(PieceType.QUEEN.canReach(new Square(3,3), new Square(7,3)));
            assertTrue(PieceType.QUEEN.canReach(new Square(3,3), new Square(6,6)));
        }
    }

    @Nested
    @DisplayName("King")
    class KingTests {
        @Test void movesOneSquareAnyDirection() {
            assertTrue(PieceType.KING.canReach(new Square(4,4), new Square(5,5)));
            assertTrue(PieceType.KING.canReach(new Square(4,4), new Square(4,5)));
        }
        @Test void castlingGeometry() {
            // Two-square horizontal move
            assertTrue(PieceType.KING.canReach(new Square(4,0), new Square(6,0)));
            assertTrue(PieceType.KING.canReach(new Square(4,0), new Square(2,0)));
        }
        @Test void doesNotMoveTwoSquaresVertically() {
            assertFalse(PieceType.KING.canReach(new Square(4,4), new Square(4,6)));
        }
    }
}
