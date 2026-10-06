package com.chess.engine.core.search;

import com.chess.engine.core.Move;
import com.chess.engine.core.MoveGen;
import com.chess.engine.core.Position;
import com.chess.engine.core.eval.Evaluator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static com.chess.engine.core.Position.*;

/**
 * Looks for the best move: iterative deepening over a principal-variation alpha-beta search
 * with a transposition table, null-move pruning, late-move reductions, futility pruning, killer
 * and history move ordering, static exchange evaluation, and a quiescence search at the leaves.
 *
 * A searcher belongs to one game: it keeps its transposition table and history between moves.
 * It is not thread-safe, except for {@link #stop()}, which any thread may call.
 */
public final class Searcher {

    public static final int MAX_PLY = 128;
    public static final int INFINITY = 32_000;
    /** The score of being mated now; a mate in n plies scores MATE - n. */
    public static final int MATE = 31_000;
    public static final int MATE_IN_MAX = MATE - MAX_PLY;

    private static final int DEFAULT_CLOCK_CHECK_MASK = 2047;

    private static final int[][] LMR = new int[64][64];
    static {
        for (int d = 1; d < 64; d++) {
            for (int m = 1; m < 64; m++) LMR[d][m] = (int) (0.75 + Math.log(d) * Math.log(m) / 2.25);
        }
    }

    private final Evaluator eval;
    private final TranspositionTable tt;

    // per-search state
    private Position pos;
    private long nodes;
    private int selDepth;
    private long startNs, softNs, hardNs, maxNodes;
    private volatile boolean stopRequested;
    private boolean aborted;
    /** The first iteration is never cut short by the clock or a node limit: every candidate move gets a score. */
    private boolean firstIteration;
    private int[] excluded = new int[0];
    /** The clock and node limit are looked at when (nodes & mask) == 0: often when the limit is tiny. */
    private int checkMask = DEFAULT_CLOCK_CHECK_MASK;

    // per-ply scratch space, so nothing is allocated while searching
    private final int[][] moves = new int[MAX_PLY + 2][MoveGen.MAX_MOVES];
    private final int[][] scores = new int[MAX_PLY + 2][MoveGen.MAX_MOVES];
    private final int[][] quietsTried = new int[MAX_PLY + 2][MoveGen.MAX_MOVES];
    private final int[][] pv = new int[MAX_PLY + 2][MAX_PLY + 2];
    private final int[] pvLength = new int[MAX_PLY + 2];

    // move ordering memory, kept between searches
    private final int[][] killers = new int[MAX_PLY + 2][2];
    private final int[][][] history = new int[2][64][64];

    public Searcher(Evaluator eval, int hashMegabytes) {
        this.eval = eval;
        this.tt = new TranspositionTable(hashMegabytes);
    }

    /** Forget everything learned about earlier games. */
    public void newGame() {
        tt.clear();
        for (int[] k : killers) Arrays.fill(k, 0);
        for (int[][] side : history) for (int[] row : side) Arrays.fill(row, 0);
    }

    /** Asks a running search to finish as soon as it can. Safe to call from any thread. */
    public void stop() { stopRequested = true; }

    public int hashFullPermille() { return tt.fullPermille(); }

    // ================================================================================================
    //  Iterative deepening
    // ================================================================================================

    public SearchResult search(Position root, SearchLimits limits) {
        return search(root, limits, null);
    }

    /**
     * Searches {@code root} (which this call leaves as it found it) and returns the best move.
     * {@code progress} hears about each completed iteration.
     */
    public SearchResult search(Position root, SearchLimits limits, Consumer<SearchInfo> progress) {
        pos = root;
        eval.reset(root);
        tt.newSearch();
        nodes = 0;
        selDepth = 0;
        aborted = false;
        stopRequested = false;
        startNs = System.nanoTime();
        setDeadlines(limits, root.sideToMove());

        int[] legal = root.legalMoves();
        if (legal.length == 0) {
            int score = root.inCheck() ? -MATE : 0;
            return new SearchResult(Move.NONE, score, 0, 0, 0, new int[0], List.of());
        }
        for (int[] k : killers) Arrays.fill(k, 0);

        int maxDepth = limits.depth() > 0 ? Math.min(limits.depth(), MAX_PLY - 1) : MAX_PLY - 1;
        int lines = Math.min(Math.max(1, limits.multiPv()), legal.length);
        boolean timed = softNs > 0;

        int bestMove = legal[0];
        int bestScore = 0;
        int[] bestPv = {legal[0]};
        int completedDepth = 0;
        List<SearchResult.Line> bestLines = List.of(new SearchResult.Line(legal[0], 0, new int[]{legal[0]}));

        // a single legal move needs no thought when the clock is running
        if (legal.length == 1 && timed) {
            return new SearchResult(legal[0], 0, 0, 0, 0, new int[]{legal[0]}, bestLines);
        }

        int[] previousScore = new int[lines];
        for (int depth = 1; depth <= maxDepth; depth++) {
            firstIteration = depth == 1;
            List<SearchResult.Line> found = new ArrayList<>();
            excluded = new int[0];

            for (int line = 0; line < lines; line++) {
                int score = searchRootWithWindow(depth, previousScore[line], lines == 1);
                if (aborted) break;
                previousScore[line] = score;
                int move = pv[0][0];
                found.add(new SearchResult.Line(move, score, Arrays.copyOf(pv[0], pvLength[0])));
                excluded = Arrays.copyOf(excluded, excluded.length + 1);
                excluded[excluded.length - 1] = move;
                if (progress != null) {
                    progress.accept(new SearchInfo(depth, line + 1, score, nodes, elapsedMs(), tt.fullPermille(),
                                                   Arrays.copyOf(pv[0], pvLength[0])));
                }
            }
            excluded = new int[0];

            if (aborted) break;              // an unfinished iteration is thrown away
            completedDepth = depth;
            bestLines = found;
            bestMove = found.get(0).move();
            bestScore = found.get(0).score();
            bestPv = found.get(0).pv();

            // a mate of p plies is seen at depth p at the latest, and a shorter one would have been seen earlier
            if (Math.abs(bestScore) >= MATE_IN_MAX && depth >= MATE - Math.abs(bestScore)) break;
            if (timed && System.nanoTime() - startNs > softNs * 0.55) break;
        }

        return new SearchResult(bestMove, bestScore, completedDepth, nodes, elapsedMs(), bestPv, bestLines);
    }

    /** One root search, widening an aspiration window around the last score until the score falls inside it. */
    private int searchRootWithWindow(int depth, int guess, boolean useWindow) {
        int delta = 25;
        int alpha = -INFINITY, beta = INFINITY;
        if (useWindow && depth >= 5) {
            alpha = Math.max(-INFINITY, guess - delta);
            beta = Math.min(INFINITY, guess + delta);
        }
        while (true) {
            int score = alphaBeta(depth, alpha, beta, 0, false);
            if (aborted) return score;
            if (score <= alpha && alpha > -INFINITY) {
                beta = (alpha + beta) / 2;
                alpha = Math.max(-INFINITY, score - delta);
            } else if (score >= beta && beta < INFINITY) {
                beta = Math.min(INFINITY, score + delta);
            } else {
                return score;
            }
            delta += delta / 2;
            if (delta > 600) { alpha = -INFINITY; beta = INFINITY; }
        }
    }

    private void setDeadlines(SearchLimits limits, int sideToMove) {
        maxNodes = limits.nodes();
        checkMask = maxNodes > 0
            ? Math.max(0, Integer.highestOneBit((int) Math.min(2048, Math.max(1, maxNodes / 4))) - 1)
            : DEFAULT_CLOCK_CHECK_MASK;
        softNs = hardNs = 0;
        long softMs = 0, hardMs = 0;
        if (limits.moveTimeMs() > 0) {
            softMs = hardMs = limits.moveTimeMs();
        } else {
            long clock = sideToMove == WHITE ? limits.whiteTimeMs() : limits.blackTimeMs();
            long increment = sideToMove == WHITE ? limits.whiteIncMs() : limits.blackIncMs();
            if (clock > 0) {
                int toGo = limits.movesToGo() > 0 ? Math.min(limits.movesToGo(), 50) : 28;
                softMs = Math.min(clock / toGo + increment * 3 / 4, clock * 6 / 10);
                hardMs = Math.min(softMs * 4, Math.max(1, clock - 50) * 8 / 10);
                softMs = Math.max(1, softMs);
                hardMs = Math.max(softMs, hardMs);
            }
        }
        if (softMs > 0) softNs = softMs * 1_000_000L;
        if (hardMs > 0) hardNs = hardMs * 1_000_000L;
    }

    private long elapsedMs() { return (System.nanoTime() - startNs) / 1_000_000L; }

    private void checkClock() {
        if (stopRequested) {
            aborted = true;
        } else if (firstIteration) {
            return;                                   // depth 1 costs next to nothing and guarantees a scored move list
        } else if ((hardNs > 0 && System.nanoTime() - startNs >= hardNs) || (maxNodes > 0 && nodes >= maxNodes)) {
            aborted = true;
        }
    }

    // ================================================================================================
    //  The main search
    // ================================================================================================

    private int alphaBeta(int depth, int alpha, int beta, int ply, boolean nullAllowed) {
        pvLength[ply] = 0;
        final boolean pvNode = beta - alpha > 1;
        final boolean root = ply == 0;
        final int us = pos.sideToMove();
        final boolean inCheck = pos.inCheck();
        if (inCheck) depth++;                                   // a check must be answered: look one ply further
        if (depth <= 0) return quiescence(alpha, beta, ply);

        if ((++nodes & checkMask) == 0) checkClock();
        if (aborted) return 0;
        if (ply > selDepth) selDepth = ply;
        if (ply >= MAX_PLY - 1) return eval.evaluate(pos);

        if (!root) {
            if (pos.isRepetition() || pos.isFiftyMoveDraw() || pos.isInsufficientMaterial()) return 0;
            // A mate found elsewhere in the tree bounds what this node can be worth
            alpha = Math.max(alpha, -MATE + ply);
            beta = Math.min(beta, MATE - ply - 1);
            if (alpha >= beta) return alpha;
        }

        // ---- transposition table ------------------------------------------------------------------
        final long key = pos.key();
        long entry = tt.probe(key);
        int ttMove = Move.NONE;
        if (entry != 0) {
            ttMove = TranspositionTable.move(entry);
            if (!pvNode && !root && TranspositionTable.depth(entry) >= depth) {
                int ttScore = fromTable(TranspositionTable.score(entry), ply);
                int bound = TranspositionTable.bound(entry);
                if (bound == TranspositionTable.EXACT
                    || (bound == TranspositionTable.LOWER && ttScore >= beta)
                    || (bound == TranspositionTable.UPPER && ttScore <= alpha)) {
                    return ttScore;
                }
            }
        }

        final int staticEval = inCheck ? -INFINITY : eval.evaluate(pos);
        // In the mate range the search is looking for the shortest mate (or the longest escape), and
        // pruning or reducing quiet moves could hide it: a quiet move is often the one that mates.
        final boolean mateRange = isMateScore(alpha) || isMateScore(beta);

        // ---- pruning that needs no search ----------------------------------------------------------
        if (!pvNode && !inCheck && !root && !mateRange) {
            // reverse futility: so far ahead that a shallow search is not going to change the verdict
            if (depth <= 7 && staticEval - 90 * depth >= beta && beta > -MATE_IN_MAX) return staticEval;

            // null move: if passing the turn still holds, the position is too good for the opponent to allow
            if (nullAllowed && depth >= 3 && staticEval >= beta && hasPieces(us) && beta < MATE_IN_MAX) {
                int reduction = 3 + depth / 4 + Math.min(3, (staticEval - beta) / 200);
                pos.makeNullMove();
                int score = -alphaBeta(depth - 1 - reduction, -beta, -beta + 1, ply + 1, false);
                pos.unmakeNullMove();
                if (aborted) return 0;
                if (score >= beta) return score >= MATE_IN_MAX ? beta : score;
            }
        }

        // ---- the moves -----------------------------------------------------------------------------
        final int[] list = moves[ply];
        final int[] score = scores[ply];
        final int n = MoveGen.pseudoLegal(pos, list);
        scoreMoves(list, score, n, ttMove, ply, us);

        int best = -INFINITY;
        int bestMove = Move.NONE;
        final int originalAlpha = alpha;
        int legalCount = 0, quietCount = 0;
        final int[] quiets = quietsTried[ply];

        for (int i = 0; i < n; i++) {
            pickBest(list, score, i, n);
            final int move = list[i];
            if (root && isExcluded(move)) continue;

            pos.makeMove(move);
            if (pos.isAttacked(pos.kingSquare(us), us ^ 1)) {   // left its own king in check
                pos.unmakeMove();
                continue;
            }
            legalCount++;
            final boolean quiet = Move.isQuiet(move);
            final boolean givesCheck = pos.inCheck();

            // moves this late, at a node this unpromising, are not worth a search
            if (!pvNode && !inCheck && !givesCheck && !mateRange && quiet && legalCount > 1 && best > -MATE_IN_MAX) {
                boolean futile = depth <= 4 && staticEval + 80 + 90 * depth <= alpha;
                boolean tooLate = depth <= 5 && legalCount >= 4 + depth * depth;
                if (futile || tooLate) {
                    pos.unmakeMove();
                    continue;
                }
            }

            eval.onMake(pos, move);
            int value;
            if (legalCount == 1) {
                value = -alphaBeta(depth - 1, -beta, -alpha, ply + 1, true);
            } else {
                int reduction = 0;
                if (depth >= 3 && quiet && !inCheck && !givesCheck && !mateRange) {
                    reduction = LMR[Math.min(depth, 63)][Math.min(legalCount, 63)];
                    if (pvNode) reduction--;
                    if (move == killers[ply][0] || move == killers[ply][1]) reduction--;
                    reduction -= history[us][Move.from(move)][Move.to(move)] / 8192;
                    reduction = Math.max(0, Math.min(reduction, depth - 2));
                }
                value = -alphaBeta(depth - 1 - reduction, -alpha - 1, -alpha, ply + 1, true);
                if (value > alpha && reduction > 0) value = -alphaBeta(depth - 1, -alpha - 1, -alpha, ply + 1, true);
                if (value > alpha && value < beta) value = -alphaBeta(depth - 1, -beta, -alpha, ply + 1, true);
            }
            eval.onUnmake();
            pos.unmakeMove();
            if (aborted) return 0;

            if (value > best) {
                best = value;
                bestMove = move;
                if (value > alpha) {
                    alpha = value;
                    if (pvNode) {
                        pv[ply][0] = move;
                        System.arraycopy(pv[ply + 1], 0, pv[ply], 1, pvLength[ply + 1]);
                        pvLength[ply] = pvLength[ply + 1] + 1;
                    }
                    if (alpha >= beta) {
                        if (quiet) rewardQuiet(move, ply, us, depth, quiets, quietCount);
                        break;
                    }
                }
            }
            if (quiet) quiets[quietCount++] = move;
        }

        if (legalCount == 0) {
            if (root) return best;                      // every root move was excluded: nothing new to report
            return inCheck ? -MATE + ply : 0;           // checkmate, or stalemate
        }

        int bound = best >= beta ? TranspositionTable.LOWER
                  : best > originalAlpha ? TranspositionTable.EXACT : TranspositionTable.UPPER;
        if (!aborted && !(root && excluded.length > 0)) {
            tt.store(key, bestMove, toTable(best, ply), depth, bound);
        }
        return best;
    }

    // ================================================================================================
    //  Quiescence: settle the captures before trusting a static score
    // ================================================================================================

    private int quiescence(int alpha, int beta, int ply) {
        pvLength[ply] = 0;
        if ((++nodes & checkMask) == 0) checkClock();
        if (aborted) return 0;
        if (ply > selDepth) selDepth = ply;
        if (ply >= MAX_PLY - 1) return eval.evaluate(pos);
        if (pos.isRepetition() || pos.isFiftyMoveDraw() || pos.isInsufficientMaterial()) return 0;

        final int us = pos.sideToMove();
        final boolean inCheck = pos.inCheck();
        int best;
        if (inCheck) {
            best = -MATE + ply;                         // unless some move escapes
        } else {
            best = eval.evaluate(pos);                  // "stand pat": the option of making no capture
            if (best >= beta) return best;
            if (best > alpha) alpha = best;
        }

        final int[] list = moves[ply];
        final int[] score = scores[ply];
        final int n = inCheck ? MoveGen.pseudoLegal(pos, list) : MoveGen.captures(pos, list);
        scoreMoves(list, score, n, Move.NONE, ply, us);

        for (int i = 0; i < n; i++) {
            pickBest(list, score, i, n);
            final int move = list[i];
            // a capture that loses material outright is not worth looking at, unless we must answer a check
            if (!inCheck && !Move.isPromotion(move) && !See.atLeast(pos, move, 0)) continue;

            pos.makeMove(move);
            if (pos.isAttacked(pos.kingSquare(us), us ^ 1)) {
                pos.unmakeMove();
                continue;
            }
            eval.onMake(pos, move);
            int value = -quiescence(-beta, -alpha, ply + 1);
            eval.onUnmake();
            pos.unmakeMove();
            if (aborted) return 0;

            if (value > best) {
                best = value;
                if (value > alpha) {
                    alpha = value;
                    if (alpha >= beta) return best;
                }
            }
        }
        return best;
    }

    // ================================================================================================
    //  Move ordering
    // ================================================================================================

    private static final int TT_MOVE = 2_000_000, GOOD_CAPTURE = 1_000_000, KILLER_1 = 900_000, KILLER_2 = 800_000,
                             BAD_CAPTURE = -100_000;

    private void scoreMoves(int[] list, int[] score, int n, int ttMove, int ply, int us) {
        for (int i = 0; i < n; i++) {
            int move = list[i];
            if (move == ttMove) {
                score[i] = TT_MOVE;
            } else if (Move.isCapture(move) || Move.isPromotion(move)) {
                int victim = Move.isEnPassant(move) ? PAWN : typeOf(pos.pieceAt(Move.to(move)) == NO_PIECE ? piece(us, PAWN) : pos.pieceAt(Move.to(move)));
                int attacker = typeOf(pos.pieceAt(Move.from(move)));
                int order = 10 * victim - attacker;                       // most valuable victim, least valuable attacker
                if (Move.isPromotion(move)) {
                    score[i] = Move.promotionType(move) == QUEEN ? GOOD_CAPTURE + 600 + order : BAD_CAPTURE + order;
                } else {
                    score[i] = See.atLeast(pos, move, 0) ? GOOD_CAPTURE + order : BAD_CAPTURE + order;
                }
            } else if (move == killers[ply][0]) {
                score[i] = KILLER_1;
            } else if (move == killers[ply][1]) {
                score[i] = KILLER_2;
            } else {
                score[i] = history[us][Move.from(move)][Move.to(move)];
            }
        }
    }

    /** Selection sort, one step: puts the best remaining move at index {@code i}. */
    private static void pickBest(int[] list, int[] score, int i, int n) {
        int best = i;
        for (int j = i + 1; j < n; j++) if (score[j] > score[best]) best = j;
        if (best != i) {
            int m = list[i]; list[i] = list[best]; list[best] = m;
            int s = score[i]; score[i] = score[best]; score[best] = s;
        }
    }

    /** A quiet move caused a cutoff: remember it as a killer and favour it in the history, and disfavour the quiets tried before it. */
    private void rewardQuiet(int move, int ply, int us, int depth, int[] tried, int triedCount) {
        if (killers[ply][0] != move) {
            killers[ply][1] = killers[ply][0];
            killers[ply][0] = move;
        }
        int bonus = Math.min(2000, depth * depth * 16);
        updateHistory(us, move, bonus);
        for (int i = 0; i < triedCount; i++) updateHistory(us, tried[i], -bonus);
    }

    private void updateHistory(int us, int move, int bonus) {
        int from = Move.from(move), to = Move.to(move);
        int h = history[us][from][to];
        history[us][from][to] = h + bonus - h * Math.abs(bonus) / 16384;   // gravity keeps it bounded
    }

    private boolean isExcluded(int move) {
        for (int e : excluded) if (e == move) return true;
        return false;
    }

    // ================================================================================================
    //  Small helpers
    // ================================================================================================

    /** Side has something besides pawns and a king: null move is unsafe in zugzwang-prone endings without it. */
    private boolean hasPieces(int color) {
        long pawnsAndKing = pos.bitboard(color, PAWN) | pos.bitboard(color, KING);
        return (pos.occupancy(color) & ~pawnsAndKing) != 0;
    }

    private static boolean isMateScore(int score) {
        return (score >= MATE_IN_MAX && score < INFINITY) || (score <= -MATE_IN_MAX && score > -INFINITY);
    }

    /** Mate scores are stored relative to the node, not the root, so a transposition at another depth is read correctly. */
    private static int toTable(int score, int ply) {
        if (score >= MATE_IN_MAX) return score + ply;
        if (score <= -MATE_IN_MAX) return score - ply;
        return score;
    }

    private static int fromTable(int score, int ply) {
        if (score >= MATE_IN_MAX) return score - ply;
        if (score <= -MATE_IN_MAX) return score + ply;
        return score;
    }
}
