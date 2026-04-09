package com.chess.domain.rules;

import com.chess.domain.board.*;
import com.chess.domain.model.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T14, T15, T16 — MoveGenerator correctness.
 *
 * T14: no illegal moves generated; no legal moves omitted; special moves included.
 * T15: starting position perft-1 = 20 (standard result, easy to verify by hand).
 * T16: promotion generates exactly 4 moves with correct PieceTypes.
 */
@DisplayName("T14/T15/T16 — MoveGenerator")
class MoveGeneratorTest {

    // ====================================================================
    // T14 — CORRECTNESS PROPERTIES
    // ====================================================================

    @Nested
    @DisplayName("T14 — No illegal moves generated")
    class NoIllegalMoves {

        @Test
        @DisplayName("all generated moves pass MoveValidator.isLegal()")
        void allMovesAreValid() {
            // Use the starting position — 20 moves, all well-known
            Board board = BoardFactory.startingPosition();
            List<Move> moves = MoveGenerator.generateLegalMoves(board);
            for (Move m : moves) {
                assertTrue(MoveValidator.isLegal(board, m),
                    "Generated move " + m + " is rejected by MoveValidator");
            }
        }

        @Test
        @DisplayName("no move generated for a pinned piece exposes king")
        void pinnedPieceGeneratesNoPinBreakingMoves() {
            // White king e1, white rook e4 (pinned on e-file), black rook e8
            // The white rook is pinned — only e-file moves are legal
            Board board = FenParser.parse("4r3/k7/8/8/4R3/8/8/4K3 w - - 0 1");
            List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);
            for (Move m : moves) {
                assertTrue(MoveValidator.isLegal(board, m),
                    "Move " + m + " should not be generated for a pinned piece");
                // Ensure no move takes the rook off the e-file
                if (board.pieceAt(m.from())
                         .map(p -> p.type() == PieceType.ROOK).orElse(false)) {
                    assertEquals(m.from().file(), m.to().file(),
                        "Pinned rook generated an off-file move: " + m);
                }
            }
        }

        @Test
        @DisplayName("in checkmate position, zero moves generated")
        void checkmateGeneratesZeroMoves() {
            // Scholar's mate — black has no moves
            Board board = FenParser.parse(
                "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4");
            List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.BLACK);
            assertEquals(0, moves.size());
        }

        @Test
        @DisplayName("in stalemate position, zero moves generated")
        void stalemateGeneratesZeroMoves() {
            // Classic stalemate
            Board board = FenParser.parse("k7/8/1QK5/8/8/8/8/8 b - - 0 1");
            List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.BLACK);
            assertEquals(0, moves.size());
        }
    }

    @Nested
    @DisplayName("T14 — No legal moves omitted")
    class NoMissingMoves {

        @Test
        @DisplayName("rook on empty board generates correct count")
        void rookOnEmptyBoard() {
            // White rook on d4, both kings present
            // Rook can reach 7 squares in each of 4 directions,
            // minus its own square; but blocked by edges:
            // d-file: d1,d2,d3 (down 3) + d5,d6,d7,d8 (up 4) = 7
            // rank 4: a4,b4,c4 (left 3) + e4,f4,g4,h4 (right 4) = 7
            // Total = 14 rook moves
            Board board = FenParser.parse("7k/8/8/8/3R4/8/8/7K w - - 0 1");
            List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);
            long rookMoves = moves.stream()
                .filter(m -> board.pieceAt(m.from())
                                  .map(p -> p.type() == PieceType.ROOK).orElse(false))
                .count();
            assertEquals(14, rookMoves,
                "White rook on d4 on near-empty board should have 14 moves");
        }

        @Test
        @DisplayName("knight on e4 has exactly 8 moves on empty board")
        void knightOnEmptyBoard() {
            Board board = FenParser.parse("7k/8/8/8/4N3/8/8/7K w - - 0 1");
            List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);
            long knightMoves = moves.stream()
                .filter(m -> board.pieceAt(m.from())
                                  .map(p -> p.type() == PieceType.KNIGHT).orElse(false))
                .count();
            assertEquals(8, knightMoves);
        }

        @Test
        @DisplayName("king in corner has 3 moves")
        void kingInCorner() {
            Board board = FenParser.parse("K7/8/8/8/8/8/8/7k w - - 0 1");
            List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);
            // King on a8: b8, a7, b7
            assertEquals(3, moves.size(),
                "King in corner should have exactly 3 moves");
        }
    }

    @Nested
    @DisplayName("T14 — Special moves are generated")
    class SpecialMovesGenerated {

        @Test
        @DisplayName("castling is generated when legal")
        void castlingGenerated() {
            Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1");
            List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);
            boolean hasKingSide = moves.stream()
                .anyMatch(m -> m.isCastling()
                    && m.to().equals(Square.of("g1")));
            boolean hasQueenSide = moves.stream()
                .anyMatch(m -> m.isCastling()
                    && m.to().equals(Square.of("c1")));
            assertTrue(hasKingSide,  "King-side castling should be generated");
            assertTrue(hasQueenSide, "Queen-side castling should be generated");
        }

        @Test
        @DisplayName("castling is NOT generated when rights absent")
        void castlingNotGeneratedNoRights() {
            Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K2R w - - 0 1");
            List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);
            assertFalse(moves.stream().anyMatch(Move::isCastling),
                "No castling should be generated without rights");
        }

        @Test
        @DisplayName("en-passant is generated when ep target is set")
        void enPassantGenerated() {
            // White pawn e5, ep target d6 (black just played d7-d5)
            Board board = FenParser.parse("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2");
            List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);
            boolean hasEp = moves.stream().anyMatch(Move::isEnPassant);
            assertTrue(hasEp, "En-passant move should be generated");
        }

        @Test
        @DisplayName("en-passant is NOT generated when ep target is absent")
        void enPassantNotGeneratedNoTarget() {
            Board board = FenParser.parse("4k3/8/8/3pP3/8/8/8/4K3 w - - 0 2");
            List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);
            assertFalse(moves.stream().anyMatch(Move::isEnPassant),
                "No en-passant should be generated without ep target");
        }
    }

    // ====================================================================
    // T15 — STARTING POSITION PERFT-1 = 20
    // ====================================================================

    @Test
    @DisplayName("T15 — starting position has exactly 20 legal moves (perft-1)")
    void startingPositionPerft1() {
        Board board = BoardFactory.startingPosition();
        List<Move> moves = MoveGenerator.generateLegalMoves(board);
        assertEquals(20, moves.size(),
            "Starting position must have exactly 20 legal moves");
    }

    @Test
    @DisplayName("T15 — starting position includes all 16 pawn moves")
    void startingPositionPawnMoves() {
        Board board = BoardFactory.startingPosition();
        List<Move> moves = MoveGenerator.generateLegalMoves(board);
        long pawnMoves = moves.stream()
            .filter(m -> board.pieceAt(m.from())
                              .map(p -> p.type() == PieceType.PAWN).orElse(false))
            .count();
        // 8 pawns × 2 moves each (single and double push) = 16
        assertEquals(16, pawnMoves);
    }

    @Test
    @DisplayName("T15 — starting position includes exactly 4 knight moves")
    void startingPositionKnightMoves() {
        Board board = BoardFactory.startingPosition();
        List<Move> moves = MoveGenerator.generateLegalMoves(board);
        long knightMoves = moves.stream()
            .filter(m -> board.pieceAt(m.from())
                              .map(p -> p.type() == PieceType.KNIGHT).orElse(false))
            .count();
        // 2 knights × 2 jump targets each = 4
        assertEquals(4, knightMoves);
    }

    @Test
    @DisplayName("T15 — specific expected moves present in starting position")
    void startingPositionExpectedMoves() {
        Board board = BoardFactory.startingPosition();
        List<Move> moves = MoveGenerator.generateLegalMoves(board);
        Set<String> moveStrings = moves.stream()
            .map(Move::toString)
            .collect(Collectors.toSet());

        // A few well-known opening moves that must be present
        assertTrue(moveStrings.contains("e2e4"), "e2e4 must be present");
        assertTrue(moveStrings.contains("d2d4"), "d2d4 must be present");
        assertTrue(moveStrings.contains("g1f3"), "g1f3 (Nf3) must be present");
        assertTrue(moveStrings.contains("b1c3"), "b1c3 (Nc3) must be present");
        assertTrue(moveStrings.contains("a2a3"), "a2a3 must be present");
        assertTrue(moveStrings.contains("h2h4"), "h2h4 must be present");
    }

    // ====================================================================
    // T16 — PROMOTION: 4 MOVES GENERATED
    // ====================================================================

    @Test
    @DisplayName("T16 — white pawn on e7 generates exactly 4 promotion moves to e8")
    void whitePawnPromotionCount() {
        // Pawn on e7, clear path to e8
        Board board = FenParser.parse("7k/4P3/8/8/8/8/8/4K3 w - - 0 1");
        List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);
        List<Move> promos = moves.stream()
            .filter(m -> m.isPromotion()
                && m.from().equals(Square.of("e7"))
                && m.to().equals(Square.of("e8")))
            .collect(Collectors.toList());

        assertEquals(4, promos.size(),
            "Exactly 4 promotion moves should be generated for e7→e8");
    }

    @Test
    @DisplayName("T16 — generated promotions cover all four piece types")
    void whitePawnPromotionTypes() {
        Board board = FenParser.parse("7k/4P3/8/8/8/8/8/4K3 w - - 0 1");
        List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);
        Set<PieceType> promoTypes = moves.stream()
            .filter(m -> m.isPromotion()
                && m.from().equals(Square.of("e7"))
                && m.to().equals(Square.of("e8")))
            .map(Move::promotion)
            .collect(Collectors.toSet());

        assertTrue(promoTypes.contains(PieceType.QUEEN),  "Queen promotion expected");
        assertTrue(promoTypes.contains(PieceType.ROOK),   "Rook promotion expected");
        assertTrue(promoTypes.contains(PieceType.BISHOP), "Bishop promotion expected");
        assertTrue(promoTypes.contains(PieceType.KNIGHT), "Knight promotion expected");
    }

    @Test
    @DisplayName("T16 — black pawn on e2 generates 4 promotions to e1")
    void blackPawnPromotionCount() {
        Board board = FenParser.parse("4k3/8/8/8/8/8/4p3/7K b - - 0 1");
        List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.BLACK);
        long promos = moves.stream()
            .filter(m -> m.isPromotion()
                && m.from().equals(Square.of("e2"))
                && m.to().equals(Square.of("e1")))
            .count();
        assertEquals(4, promos);
    }

    @Test
    @DisplayName("T16 — pawn with capture on promotion rank generates 8 moves (4×2 squares)")
    void promotionWithCapture() {
        // White pawn e7, black rook on d8, empty e8 and f8
        // e7→e8 (4 promos) + e7→d8xR (4 capture-promos) = 8
        Board board = FenParser.parse("3r3k/4P3/8/8/8/8/8/4K3 w - - 0 1");
        List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);
        long promos = moves.stream().filter(Move::isPromotion).count();
        assertEquals(8, promos,
            "8 promotion moves expected (4 straight + 4 capture)");
    }
}
