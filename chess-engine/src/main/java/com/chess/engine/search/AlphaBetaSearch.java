package com.chess.engine.search;

import com.chess.domain.board.Board;
import com.chess.domain.model.Move;
import com.chess.domain.rules.GameStateChecker;
import com.chess.domain.rules.GameStateChecker.State;
import com.chess.domain.rules.MoveGenerator;
import com.chess.engine.eval.Evaluator;

import java.util.List;
import java.util.Optional;

/**
 * Negamax alpha-beta search over the immutable Board.
 *
 * Contract for findBestMove():
 *   • Returns Optional.empty() iff the position is terminal
 *     (checkmate, stalemate, 50-move draw) — the caller MUST handle this.
 *   • Never returns null.
 *   • Never throws on a legal but losing position.
 *
 * Terminal-position contract (T17):
 *   AlphaBetaSearch.findBestMove() on a mated position → Optional.empty()
 *   AlphaBetaSearch.findBestMove() on a stalemated position → Optional.empty()
 *   Callers (AiPlayer, tests) use orElseThrow() / isPresent() accordingly.
 */
public final class AlphaBetaSearch {

    // Use half of MIN/MAX_VALUE to avoid overflow when negating
    static final int NEG_INF = Integer.MIN_VALUE / 2;
    static final int POS_INF = Integer.MAX_VALUE / 2;

    private final Evaluator evaluator = new Evaluator();

    /**
     * Returns the best move for the active player at the given search depth.
     *
     * @return Optional.empty() if the position is terminal (no legal moves).
     */
    public Optional<Move> findBestMove(Board board, int depth) {
        List<Move> moves = MoveGenerator.generateLegalMoves(board);
        if (moves.isEmpty()) return Optional.empty();  // T17: terminal position

        Move best  = null;
        int  alpha = NEG_INF;

        for (Move move : moves) {
            Board next  = board.apply(move);
            int   score = -alphaBeta(next, depth - 1, NEG_INF, -alpha);
            if (best == null || score > alpha) {
                alpha = score;
                best  = move;
            }
        }
        return Optional.ofNullable(best);  // best is always non-null here
    }

    /**
     * Negamax core with alpha-beta pruning.
     * Score is always from the perspective of the active player on 'board'.
     */
    int alphaBeta(Board board, int depth, int alpha, int beta) {
        List<Move> moves = MoveGenerator.generateLegalMoves(board);

        if (moves.isEmpty() || depth == 0) {
            return leafScore(board, depth, moves);
        }

        for (Move move : moves) {
            Board next  = board.apply(move);
            int   score = -alphaBeta(next, depth - 1, -beta, -alpha);
            if (score >= beta)  return beta;   // fail-hard beta cut-off
            if (score > alpha)  alpha = score;
        }
        return alpha;
    }

    /**
     * Scores a leaf node (depth == 0 or no legal moves).
     * Checkmate score includes remaining depth so faster mates score higher.
     */
    private int leafScore(Board board, int depth, List<Move> moves) {
        if (moves.isEmpty()) {
            State state = GameStateChecker.evaluate(board, board.activeColor());
            return switch (state) {
                // With remaining-depth scoring, a larger remaining depth means
                // checkmate happened sooner in the line. Make that strictly worse
                // for the side to move so the parent prefers faster mates.
                case CHECKMATE   -> NEG_INF - depth;
                case STALEMATE,
                     DRAW_50_MOVE -> 0;
                default           -> 0;              // shouldn't happen
            };
        }
        // depth == 0: evaluate statically
        return evaluator.evaluate(board, board.activeColor());
    }
}
