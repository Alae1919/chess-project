package com.chess.engine.eval;

import com.chess.domain.board.Board;
import com.chess.domain.model.*;

/**
 * Piece-square tables for positional evaluation.
 *
 * Each piece type has two tables: midgame (MG) and endgame (EG).
 * The actual bonus is a linear interpolation between MG and EG using
 * a phase factor (256 = pure midgame, 0 = pure endgame):
 *   bonus = (mg * phase + eg * (256 - phase)) / 256
 *
 * White's tables are indexed from rank 1 (index 0) to rank 8 (index 7).
 * Black's tables mirror White's (flip rank axis).
 *
 * Backward-compatible: {@link #bonus(Piece, int, int)} still works (assumes midgame).
 */
public final class PieceSquareTables {

    private PieceSquareTables() {}

    // -----------------------------------------------------------------------
    // Midgame tables (same as original, reorganised)
    // -----------------------------------------------------------------------

    private static final int[] KNIGHT_MG = {
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50
    };

    private static final int[] BISHOP_MG = {
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20
    };

    private static final int[] ROOK_MG = {
         0,  0,  0,  0,  0,  0,  0,  0,
         5, 10, 10, 10, 10, 10, 10,  5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
         0,  0,  0,  5,  5,  0,  0,  0
    };

    private static final int[] QUEEN_MG = {
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
         -5,  0,  5,  5,  5,  5,  0, -5,
          0,  0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20
    };

    private static final int[] KING_MG = {
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -10,-20,-20,-20,-20,-20,-20,-10,
         20, 20,  0,  0,  0,  0, 20, 20,
         20, 30, 10,  0,  0, 10, 30, 20
    };

    private static final int[] PAWN_MG = {
          0,  0,  0,  0,  0,  0,  0,  0,
         50, 50, 50, 50, 50, 50, 50, 50,
         10, 10, 20, 30, 30, 20, 10, 10,
          5,  5, 10, 25, 25, 10,  5,  5,
          0,  0,  0, 20, 20,  0,  0,  0,
          5, -5,-10,  0,  0,-10, -5,  5,
          5, 10, 10,-20,-20, 10, 10,  5,
          0,  0,  0,  0,  0,  0,  0,  0
    };

    // -----------------------------------------------------------------------
    // Endgame tables
    // -----------------------------------------------------------------------

    private static final int[] PAWN_EG = {
          0,  0,  0,  0,  0,  0,  0,  0,
         80, 80, 80, 80, 80, 80, 80, 80,
         40, 40, 40, 40, 40, 40, 40, 40,
         20, 20, 20, 20, 20, 20, 20, 20,
         10, 10, 10, 10, 10, 10, 10, 10,
          5,  5,  5,  5,  5,  5,  5,  5,
          0,  0,  0,  0,  0,  0,  0,  0,
          0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final int[] KNIGHT_EG = {
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50
    };

    private static final int[] BISHOP_EG = {
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20
    };

    private static final int[] ROOK_EG = {
         0,  0,  0,  0,  0,  0,  0,  0,
         5, 10, 10, 10, 10, 10, 10,  5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
         0,  0,  0,  5,  5,  0,  0,  0
    };

    private static final int[] QUEEN_EG = {
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
         -5,  0,  5,  5,  5,  5,  0, -5,
          0,  0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20
    };

    /** Endgame king table: king should centralise and become active. */
    private static final int[] KING_EG = {
        -50,-40,-30,-20,-20,-30,-40,-50,
        -30,-20,-10,  0,  0,-10,-20,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-30,  0,  0,  0,  0,-30,-30,
        -50,-30,-30,-30,-30,-30,-30,-50
    };

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Tapered positional bonus for a piece at a given square.
     *
     * @param phase 0 = pure endgame, 256 = pure midgame (from {@link GamePhase#phase})
     */
    public static int bonus(Piece piece, int file, int rank, int phase) {
        int tableRank = piece.color() == Color.WHITE ? rank : (7 - rank);
        int idx       = tableRank * Board.SIZE + file;
        int[] mgTable = mgTableFor(piece.type());
        int[] egTable = egTableFor(piece.type());
        if (mgTable == null) return 0;
        int mg = mgTable[idx];
        int eg = egTable[idx];
        return (mg * phase + eg * (256 - phase)) / 256;
    }

    /**
     * Backward-compatible single-table bonus (assumes pure midgame, phase = 256).
     */
    public static int bonus(Piece piece, int file, int rank) {
        return bonus(piece, file, rank, 256);
    }

    // -----------------------------------------------------------------------

    private static int[] mgTableFor(PieceType type) {
        return switch (type) {
            case PAWN   -> PAWN_MG;
            case KNIGHT -> KNIGHT_MG;
            case BISHOP -> BISHOP_MG;
            case ROOK   -> ROOK_MG;
            case QUEEN  -> QUEEN_MG;
            case KING   -> KING_MG;
        };
    }

    private static int[] egTableFor(PieceType type) {
        return switch (type) {
            case PAWN   -> PAWN_EG;
            case KNIGHT -> KNIGHT_EG;
            case BISHOP -> BISHOP_EG;
            case ROOK   -> ROOK_EG;
            case QUEEN  -> QUEEN_EG;
            case KING   -> KING_EG;
        };
    }
}
