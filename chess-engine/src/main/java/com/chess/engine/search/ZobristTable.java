package com.chess.engine.search;

import com.chess.domain.board.Board;
import com.chess.domain.board.CastlingRights;
import com.chess.domain.model.*;

import java.util.Random;

/**
 * Zobrist hashing for transposition table keys.
 *
 * Uses a fixed seed so hashes are reproducible across all calls within the same JVM run.
 * The seed is arbitrary — only consistency within one search matters.
 *
 * Index layout:
 *   PIECE_SQUARE[pieceIndex][square64]
 *     pieceIndex: 0-5 = WHITE (PAWN,KNIGHT,BISHOP,ROOK,QUEEN,KING)
 *                 6-11 = BLACK (same order)
 *     square64:   file + rank * 8
 *
 * NOTE: This is NOT the Polyglot opening-book hash (which uses a different set of
 * well-known constants). See PolyglotKey for book lookups.
 */
public final class ZobristTable {

    private static final long[][] PIECE_SQUARE = new long[12][64];
    private static final long[]   CASTLING     = new long[4];   // [0]=WK [1]=WQ [2]=BK [3]=BQ
    private static final long[]   EN_PASSANT   = new long[8];   // indexed by file 0-7
    private static final long     SIDE_TO_MOVE;                  // XOR in when Black to move

    static {
        Random rng = new Random(0xDEADBEEFCAFEBABEL);
        for (int i = 0; i < 12; i++)
            for (int j = 0; j < 64; j++)
                PIECE_SQUARE[i][j] = rng.nextLong();
        for (int i = 0; i < 4; i++) CASTLING[i]   = rng.nextLong();
        for (int i = 0; i < 8; i++) EN_PASSANT[i] = rng.nextLong();
        SIDE_TO_MOVE = rng.nextLong();
    }

    private ZobristTable() {}

    /**
     * Computes a 64-bit Zobrist hash for the given board position.
     * O(64) — safe to call at every node of the search tree.
     */
    public static long hash(Board board) {
        long h = 0L;

        for (int f = 0; f < Board.SIZE; f++) {
            for (int r = 0; r < Board.SIZE; r++) {
                var opt = board.pieceAt(new Square(f, r));
                if (opt.isPresent()) {
                    h ^= PIECE_SQUARE[pieceIndex(opt.get())][f + r * 8];
                }
            }
        }

        if (board.activeColor() == Color.BLACK) h ^= SIDE_TO_MOVE;

        CastlingRights cr = board.castlingRights();
        if (cr.whiteKingSide())  h ^= CASTLING[0];
        if (cr.whiteQueenSide()) h ^= CASTLING[1];
        if (cr.blackKingSide())  h ^= CASTLING[2];
        if (cr.blackQueenSide()) h ^= CASTLING[3];

        var ep = board.enPassantTarget();
        if (ep.isPresent()) h ^= EN_PASSANT[ep.get().file()];

        return h;
    }

    private static int pieceIndex(Piece p) {
        int typeOrd = switch (p.type()) {
            case PAWN   -> 0;
            case KNIGHT -> 1;
            case BISHOP -> 2;
            case ROOK   -> 3;
            case QUEEN  -> 4;
            case KING   -> 5;
        };
        return p.color() == Color.WHITE ? typeOrd : typeOrd + 6;
    }
}
