package com.chess.engine.player;

import com.chess.domain.board.Board;
import com.chess.domain.board.BoardFactory;
import com.chess.domain.model.Color;
import com.chess.domain.model.Move;
import com.chess.domain.rules.GameStateChecker;
import com.chess.domain.rules.MoveGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Whole games through the adapter between the application's board and the engine. Every move
 * the engine returns has to be legal for the application's rules, in positions that arise by
 * themselves: castling, en passant, promotions, checks, and the end of the game.
 */
@DisplayName("AiPlayer — whole games")
class AiPlayerSoakTest {

    private static final int MAX_PLIES = 240;

    /** Plays one game; returns how many plies it lasted. */
    private static int play(AiPlayer white, AiPlayer black, Random random) {
        Board start = BoardFactory.startingPosition();
        Board board = start;
        List<String> history = new ArrayList<>();
        for (int ply = 0; ply < MAX_PLIES; ply++) {
            GameStateChecker.State state = GameStateChecker.evaluate(board, board.activeColor());
            if (GameStateChecker.isTerminal(state)) return ply;

            AiPlayer ai = board.activeColor() == Color.WHITE ? white : black;
            Move move;
            if (ai == null) {                                       // a random mover
                List<Move> legal = MoveGenerator.generateLegalMoves(board);
                move = legal.get(random.nextInt(legal.size()));
            } else {
                move = ai.chooseMove(board, start, history, 0, 0, 0);
                String uci = move.toUci();
                assertTrue(MoveGenerator.generateLegalMoves(board).stream().anyMatch(m -> m.toUci().equals(uci)),
                    "the engine played " + uci + " in " + com.chess.domain.board.FenParser.toFen(board));
            }
            history.add(move.toUci());
            board = board.apply(move);
        }
        return MAX_PLIES;
    }

    @Test
    @DisplayName("against random moves, the engine only ever plays legal moves, and wins most games")
    void againstRandomMovers() {
        Random random = new Random(11);
        int decided = 0;
        for (int game = 0; game < 6; game++) {
            boolean engineIsWhite = game % 2 == 0;
            AiPlayer engine = new AiPlayer(engineIsWhite ? Color.WHITE : Color.BLACK, 2, random);
            int plies = play(engineIsWhite ? engine : null, engineIsWhite ? null : engine, random);
            if (plies < MAX_PLIES) decided++;
        }
        assertTrue(decided >= 5, "the engine should finish off a random mover, not shuffle: " + decided + " of 6 games ended");
    }

    @Test
    @DisplayName("two engines play a whole game against each other without a single illegal move")
    void againstItself() {
        Random random = new Random(5);
        for (int game = 0; game < 3; game++) {
            play(new AiPlayer(Color.WHITE, 2, random), new AiPlayer(Color.BLACK, 3, random), random);
        }
    }
}
