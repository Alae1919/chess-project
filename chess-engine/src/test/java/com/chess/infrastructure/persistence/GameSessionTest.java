package com.chess.infrastructure.persistence;

import com.chess.domain.board.BoardFactory;
import com.chess.domain.board.FenParser;
import com.chess.domain.rules.GameStateChecker.State;
import com.chess.domain.rules.MoveGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("GameSession — draws that depend on history")
class GameSessionTest {

    private static GameSession start() {
        return new GameSession("g", BoardFactory.startingPosition(), null, 1);
    }

    private static void play(GameSession session, String... ucis) {
        for (String uci : ucis) {
            var move = MoveGenerator.generateLegalMoves(session.board()).stream()
                .filter(m -> m.toUci().equals(uci)).findFirst().orElseThrow();
            session.applyMove(move);
        }
    }

    /** Both knights out and back: the start position again after four half-moves. */
    private static final String[] KNIGHTS_OUT_AND_BACK = { "g1f3", "g8f6", "f3g1", "f6g8" };

    @Test
    @DisplayName("the third occurrence of a position is a draw")
    void threefoldRepetition() {
        var session = start();

        play(session, KNIGHTS_OUT_AND_BACK);
        assertEquals(State.ONGOING, session.state(), "second occurrence");

        play(session, KNIGHTS_OUT_AND_BACK);
        assertEquals(State.DRAW_REPETITION, session.state());
        assertEquals(true, session.isOver());
    }

    @Test
    @DisplayName("undo takes the undone position back out of the count")
    void undoForgetsThePosition() {
        var session = start();
        play(session, "g1f3");
        session.undoLastMove();

        // After a round trip, Nf3 is on the board for the second time, not the third
        play(session, KNIGHTS_OUT_AND_BACK);
        play(session, "g1f3");

        assertEquals(State.ONGOING, session.state());
    }

    @Test
    @DisplayName("castling rights are part of the position")
    void castlingRightsDistinguishPositions() {
        // Rooks shuffle out and back, but the rook move costs a castling right, so
        // the position after the round trip is not the same as before
        var session = new GameSession("g",
            FenParser.parse("r3k3/8/8/8/8/8/8/R3K3 w Qq - 0 1"), null, 1);
        String[] shuffle = { "a1b1", "a8b8", "b1a1", "b8a8" };

        play(session, shuffle);
        play(session, shuffle);

        assertEquals(State.ONGOING, session.state());
    }

    @Test
    @DisplayName("a capture that leaves only kings is a draw")
    void insufficientMaterialAfterACapture() {
        var session = new GameSession("g",
            FenParser.parse("8/8/8/8/8/8/1R6/k6K b - - 0 1"), null, 1);

        play(session, "a1b2"); // the king takes the last rook

        assertEquals(State.DRAW_INSUFFICIENT_MATERIAL, session.state());
    }
}
