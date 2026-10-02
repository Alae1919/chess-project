package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.board.FenParser;
import com.chess.domain.model.Move;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Perft: counts every leaf of the legal-move tree to a fixed depth and compares
 * it with published reference counts (chessprogramming.org/Perft_Results).
 *
 * A single missing or extra move anywhere (castling through check, en passant
 * pins, under-promotion...) changes the count, so this is the standard proof
 * that move generation is correct. Depths are kept low enough to run in a few
 * seconds.
 */
@DisplayName("Perft — move generation matches reference node counts")
class PerftTest {

    @ParameterizedTest(name = "{0} depth {2} = {3}")
    @CsvSource(delimiter = '|', textBlock = """
        start position | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1         | 1 | 20
        start position | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1         | 2 | 400
        start position | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1         | 3 | 8902
        start position | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1         | 4 | 197281
        kiwipete       | r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 1 | 48
        kiwipete       | r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 2 | 2039
        kiwipete       | r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 3 | 97862
        position 3     | 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1                        | 4 | 43238
        position 4     | r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1 | 3 | 9467
        position 5     | rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8        | 3 | 62379
        """)
    void matchesReferenceCount(String name, String fen, int depth, long expected) {
        assertEquals(expected, perft(FenParser.parse(fen), depth));
    }

    private static long perft(Board board, int depth) {
        List<Move> moves = MoveGenerator.generateLegalMoves(board);
        if (depth == 1) return moves.size();
        long nodes = 0;
        for (Move move : moves) {
            nodes += perft(board.apply(move), depth - 1);
        }
        return nodes;
    }
}
