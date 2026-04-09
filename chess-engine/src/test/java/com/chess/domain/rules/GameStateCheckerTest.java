package com.chess.domain.rules;

import com.chess.domain.board.*;
import com.chess.domain.model.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static com.chess.domain.rules.GameStateChecker.State.*;

/**
 * T13 — GameStateChecker.evaluate() for all five states.
 *
 * State order tested: ONGOING, CHECK, CHECKMATE, STALEMATE, DRAW_50_MOVE.
 *
 * Positions chosen to be minimal, unambiguous, and verifiable by hand.
 */
@DisplayName("T13 — GameStateChecker")
class GameStateCheckerTest {

    // ---- ONGOING -------------------------------------------------------

    @Test
    @DisplayName("ONGOING — starting position, White to move")
    void ongoingStartPosition() {
        Board board = BoardFactory.startingPosition();
        assertEquals(ONGOING, GameStateChecker.evaluate(board, Color.WHITE));
    }

    @Test
    @DisplayName("ONGOING — simple rook endgame, no check")
    void ongoingRookEndgame() {
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K3 w - - 0 1");
        assertEquals(ONGOING, GameStateChecker.evaluate(board, Color.WHITE));
    }

    // ---- CHECK ---------------------------------------------------------

    @Test
    @DisplayName("CHECK — white king on e1 in check from black rook on e8")
    void checkByRook() {
        Board board = FenParser.parse("4r3/k7/8/8/8/8/8/4K3 w - - 0 1");
        assertEquals(CHECK, GameStateChecker.evaluate(board, Color.WHITE));
    }

    @Test
    @DisplayName("CHECK — black king in check but has escape squares")
    void checkBlackKingHasEscape() {
        // White rook on a8 checks black king on e8; king can flee to d7/f7/d8/f8 etc.
        Board board = FenParser.parse("R3k3/8/8/8/8/8/8/4K3 b - - 0 1");
        assertEquals(CHECK, GameStateChecker.evaluate(board, Color.BLACK));
    }

    // ---- CHECKMATE -----------------------------------------------------

    @Test
    @DisplayName("CHECKMATE — Scholar's mate")
    void scholarsCheckmate() {
        // Scholar's mate: Black king f8 is mated
        // FEN after 1.e4 e5 2.Qh5 Nc6 3.Bc4 Nf6?? 4.Qxf7#
        Board board = FenParser.parse(
            "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4");
        assertEquals(CHECKMATE, GameStateChecker.evaluate(board, Color.BLACK));
    }

    @Test
    @DisplayName("CHECKMATE — back-rank mate: white rook on a8, black king on h8")
    void backRankMate() {
        // Black king h8 cornered; white rook on a8 delivers checkmate.
        // Black queen gone; no escape. King on h8 is against the wall.
        // Black pawns on f7, g7 block escape upward (rank 8 is top).
        // White king on h6 covers g7 and h7.
        Board board = FenParser.parse("R6k/5ppp/7K/8/8/8/8/8 b - - 0 1");
        assertEquals(CHECKMATE, GameStateChecker.evaluate(board, Color.BLACK));
    }

    @Test
    @DisplayName("CHECKMATE — isTerminal returns true for CHECKMATE")
    void checkmateIsTerminal() {
        assertTrue(GameStateChecker.isTerminal(CHECKMATE));
    }

    // ---- STALEMATE -----------------------------------------------------

    @Test
    @DisplayName("STALEMATE — classic stalemate: black king on a8, white queen on b6")
    void classicStalemate() {
        // Black king a8; white queen b6 (covers b7, a7); white king c6 (covers b7, b8)
        // Black king has no legal moves and is not in check → stalemate
        Board board = FenParser.parse("k7/8/1QK5/8/8/8/8/8 b - - 0 1");
        assertEquals(STALEMATE, GameStateChecker.evaluate(board, Color.BLACK));
    }

    @Test
    @DisplayName("STALEMATE — isTerminal returns true")
    void stalemateIsTerminal() {
        assertTrue(GameStateChecker.isTerminal(STALEMATE));
    }

    // ---- DRAW_50_MOVE --------------------------------------------------

    @Test
    @DisplayName("DRAW_50_MOVE — half-move clock at 100 triggers draw (even with moves available)")
    void fiftyMoveDrawHasMoves() {
        // The player still has legal moves (rook can move), but clock is 100
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K3 w - - 100 80");
        assertEquals(DRAW_50_MOVE, GameStateChecker.evaluate(board, Color.WHITE));
    }

    @Test
    @DisplayName("DRAW_50_MOVE — half-move clock below 100 does not trigger draw")
    void noFiftyMoveBelowThreshold() {
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K3 w - - 99 80");
        assertEquals(ONGOING, GameStateChecker.evaluate(board, Color.WHITE));
    }

    @Test
    @DisplayName("DRAW_50_MOVE — isTerminal returns true")
    void draw50IsTerminal() {
        assertTrue(GameStateChecker.isTerminal(DRAW_50_MOVE));
    }

    // ---- isTerminal correctness ----------------------------------------

    @Test
    @DisplayName("ONGOING and CHECK are NOT terminal")
    void ongoingAndCheckNotTerminal() {
        assertFalse(GameStateChecker.isTerminal(ONGOING));
        assertFalse(GameStateChecker.isTerminal(CHECK));
    }
}
