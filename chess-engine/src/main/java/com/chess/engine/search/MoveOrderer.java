package com.chess.engine.search;

import com.chess.domain.board.Board;
import com.chess.domain.model.Move;
import com.chess.domain.model.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * Move ordering heuristics for alpha-beta pruning.
 *
 * Good move ordering dramatically increases the number of alpha-beta cut-offs,
 * allowing the search to reach greater effective depth in the same time.
 *
 * Priority (highest first):
 *   1. Transposition-table best move from a previous iteration  (+100 000)
 *   2. Captures, ranked by MVV-LVA (Most Valuable Victim – Least Valuable Attacker)
 *      Base +10 000; MVV-LVA = victim_value * 10 − attacker_value
 *   3. Killer move slot 1 (quiet move that caused a beta cut-off at the same ply) (+9 000)
 *   4. Killer move slot 2                                                          (+8 000)
 *   5. All other quiet moves                                                       (0)
 */
public final class MoveOrderer {

    private static final int MAX_PLY = 64;

    /** Two killer slots per ply. */
    private final Move[][] killers = new Move[MAX_PLY][2];

    /**
     * Returns a new list sorted from best to worst (does not mutate the input).
     *
     * @param moves      legal moves to sort
     * @param board      current board (needed to look up the moving piece for MVV-LVA)
     * @param ply        current search depth from the root (0-based)
     * @param ttBestMove best move from a TT hit, or {@code null}
     */
    public List<Move> order(List<Move> moves, Board board, int ply, Move ttBestMove) {
        List<Move> sorted = new ArrayList<>(moves);
        sorted.sort((a, b) -> score(b, board, ply, ttBestMove)
                             - score(a, board, ply, ttBestMove));
        return sorted;
    }

    /**
     * Records a quiet move that caused a beta cut-off at {@code ply}.
     * Called from the search when a non-capture move causes a cut-off.
     */
    public void storeKiller(Move move, int ply) {
        if (ply >= MAX_PLY) return;
        if (!move.equals(killers[ply][0])) {
            killers[ply][1] = killers[ply][0]; // push old slot-1 to slot-2
            killers[ply][0] = move;
        }
    }

    private int score(Move move, Board board, int ply, Move ttBestMove) {
        if (move.equals(ttBestMove))  return 100_000;
        if (move.isCapture())         return 10_000 + mvvLva(move, board);
        if (ply < MAX_PLY) {
            if (move.equals(killers[ply][0])) return 9_000;
            if (move.equals(killers[ply][1])) return 8_000;
        }
        return 0;
    }

    /**
     * MVV-LVA score: victim_value × 10 − attacker_value.
     * Higher score → search this capture first (e.g. queen takes pawn > pawn takes queen).
     */
    private static int mvvLva(Move capture, Board board) {
        int victim   = capture.capturedPiece().value();
        int attacker = board.pieceAt(capture.from())
                            .map(Piece::value)
                            .orElse(0);
        return victim * 10 - attacker;
    }

    /**
     * Simple capture score for quiescence search (just the victim value).
     * Used to sort captures without needing the board.
     */
    public static int captureScore(Move m) {
        return m.isCapture() ? m.capturedPiece().value() : 0;
    }
}
