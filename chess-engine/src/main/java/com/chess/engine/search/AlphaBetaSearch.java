package com.chess.engine.search;

import com.chess.domain.board.Board;
import com.chess.domain.model.Move;
import com.chess.domain.rules.GameStateChecker;
import com.chess.domain.rules.GameStateChecker.State;
import com.chess.domain.rules.MoveGenerator;
import com.chess.engine.eval.Evaluator;
import com.chess.engine.eval.PositionEvaluator;

import java.util.List;
import java.util.Optional;

/**
 * Negamax alpha-beta search over the immutable Board.
 *
 * Improvements over the original fixed-depth implementation:
 *   • Iterative deepening — deepens from 1 to maxDepth; supports time control
 *   • Transposition table — avoids re-searching identical positions
 *   • Move ordering — TT best move → captures (MVV-LVA) → killer moves → quiet
 *   • Quiescence search — resolves captures/promotions at leaf nodes to avoid
 *                         the horizon effect
 *
 * Backward-compatible entry points (existing callers need no changes):
 *   findBestMove(Board, int depth)             — fixed-depth, as before
 *   alphaBeta(Board, int, int, int)            — package-private, tested directly
 *
 * Contract for findBestMove():
 *   • Returns Optional.empty() iff the position is terminal (no legal moves).
 *   • Never returns null; never throws on a legal but losing position.
 */
public final class AlphaBetaSearch {

    // Use half of MIN/MAX_VALUE to avoid overflow when negating
    static final int NEG_INF = Integer.MIN_VALUE / 2;
    static final int POS_INF = Integer.MAX_VALUE / 2;

    private final PositionEvaluator  evaluator;
    private final TranspositionTable tt      = new TranspositionTable();
    private final MoveOrderer        orderer = new MoveOrderer();

    /** Default constructor: uses the handcrafted evaluator. All existing callers unchanged. */
    public AlphaBetaSearch() { this(new Evaluator()); }

    /** Injectable constructor for plugging in a neural-network or custom evaluator. */
    public AlphaBetaSearch(PositionEvaluator evaluator) { this.evaluator = evaluator; }

    // Time-control state (reset at the start of each findBestMove call)
    private volatile boolean timeUp  = false;
    private          long    deadline = Long.MAX_VALUE;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Backward-compatible fixed-depth search.
     * Delegates to {@link #findBestMove(Board, SearchConfig)}.
     */
    public Optional<Move> findBestMove(Board board, int depth) {
        return findBestMove(board, SearchConfig.fixedDepth(depth));
    }

    /**
     * Iterative-deepening search with optional time control.
     *
     * @return Optional.empty() if the position is terminal (no legal moves).
     */
    public Optional<Move> findBestMove(Board board, SearchConfig config) {
        List<Move> moves = MoveGenerator.generateLegalMoves(board);
        if (moves.isEmpty()) return Optional.empty();

        timeUp   = false;
        deadline = config.hasTimeLimit()
            ? System.currentTimeMillis() + config.timeLimitMs()
            : Long.MAX_VALUE;

        Move bestMove = moves.get(0); // safety fallback if time expires before depth 1

        for (int depth = 1; depth <= config.maxDepth(); depth++) {
            if (timeUp) break;

            int  alpha    = NEG_INF;
            Move iterBest = null;

            // Use TT best move from the previous iteration for root move ordering
            long hash   = ZobristTable.hash(board);
            var  ttHit  = tt.probe(hash);
            Move ttBest = (ttHit != null) ? ttHit.bestMove() : null;

            List<Move> ordered = orderer.order(moves, board, 0, ttBest);

            for (Move move : ordered) {
                if (timeUp) break;
                Board next  = board.apply(move);
                int   score = -searchInternal(next, depth - 1, NEG_INF, -alpha, 1);
                if (score > alpha) {
                    alpha    = score;
                    iterBest = move;
                }
            }

            // Only update bestMove from a completed iteration
            if (!timeUp && iterBest != null) bestMove = iterBest;
        }

        return Optional.of(bestMove);
    }

    // -----------------------------------------------------------------------
    // Package-private (tested directly by AlphaBetaSearchTest)
    // -----------------------------------------------------------------------

    /**
     * Backward-compatible 4-argument signature used by tests.
     * Delegates to the internal 5-argument version with ply = 0.
     */
    int alphaBeta(Board board, int depth, int alpha, int beta) {
        return searchInternal(board, depth, alpha, beta, 0);
    }

    // -----------------------------------------------------------------------
    // Internal search
    // -----------------------------------------------------------------------

    private int searchInternal(Board board, int depth, int alpha, int beta, int ply) {
        if (timeUp) return 0;
        if (System.currentTimeMillis() >= deadline) { timeUp = true; return 0; }

        long hash       = ZobristTable.hash(board);
        Move ttBestMove = null;

        // --- Transposition table probe ---
        var entry = tt.probe(hash);
        if (entry != null) {
            ttBestMove = entry.bestMove();
            if (entry.depth() >= depth) {
                switch (entry.flag()) {
                    case EXACT       -> { return entry.score(); }
                    case LOWER_BOUND -> alpha = Math.max(alpha, entry.score());
                    case UPPER_BOUND -> beta  = Math.min(beta,  entry.score());
                }
                if (alpha >= beta) return entry.score();
            }
        }

        List<Move> moves = MoveGenerator.generateLegalMoves(board);

        // --- Terminal or leaf ---
        if (moves.isEmpty()) return terminalScore(board, depth);
        if (depth == 0)      return quiescence(board, alpha, beta);

        // --- Recursive search with ordered moves ---
        List<Move> ordered  = orderer.order(moves, board, ply, ttBestMove);
        int        origAlpha = alpha;
        Move       bestMove  = null;

        for (Move move : ordered) {
            if (timeUp) return alpha;
            Board next  = board.apply(move);
            int   score = -searchInternal(next, depth - 1, -beta, -alpha, ply + 1);

            if (score >= beta) {
                if (!move.isCapture()) orderer.storeKiller(move, ply);
                tt.store(hash, depth, beta, TranspositionTable.Flag.LOWER_BOUND, move);
                return beta;   // fail-hard beta cut-off
            }
            if (score > alpha) {
                alpha    = score;
                bestMove = move;
            }
        }

        // --- Store result in TT ---
        var flag = (alpha <= origAlpha)
            ? TranspositionTable.Flag.UPPER_BOUND
            : TranspositionTable.Flag.EXACT;
        tt.store(hash, depth, alpha, flag, bestMove);

        return alpha;
    }

    /**
     * Quiescence search — extends the search to resolve all captures and promotions
     * before calling the static evaluator, avoiding the horizon effect.
     *
     * Uses stand-pat pruning: if the static eval already beats beta, prune immediately.
     */
    private int quiescence(Board board, int alpha, int beta) {
        int standPat = evaluator.evaluate(board, board.activeColor());
        if (standPat >= beta)  return beta;
        if (standPat > alpha)  alpha = standPat;

        // Only search captures and promotions (no quiet moves)
        List<Move> captures = MoveGenerator.generateLegalMoves(board)
            .stream()
            .filter(m -> m.isCapture() || m.isPromotion())
            .sorted((a, b) -> MoveOrderer.captureScore(b) - MoveOrderer.captureScore(a))
            .toList();

        for (Move m : captures) {
            Board next  = board.apply(m);
            int   score = -quiescence(next, -beta, -alpha);
            if (score >= beta)  return beta;
            if (score > alpha)  alpha = score;
        }
        return alpha;
    }

    /**
     * Score for a node with no legal moves (checkmate / stalemate / 50-move draw).
     * Checkmate score includes remaining depth so the search prefers faster mates.
     */
    private int terminalScore(Board board, int depth) {
        State state = GameStateChecker.evaluate(board, board.activeColor());
        return switch (state) {
            case CHECKMATE              -> NEG_INF - depth;
            case STALEMATE, DRAW_50_MOVE -> 0;
            default                     -> 0;
        };
    }
}
