package com.chess.engine.core.eval.nnue;

import com.chess.engine.core.Move;
import com.chess.engine.core.Position;
import com.chess.engine.core.eval.Evaluator;

import java.util.Arrays;

/**
 * Evaluates with a trained network. The expensive part, the hidden layer, is kept up to date
 * move by move: a quiet move changes two inputs, a capture three, so each move costs a few
 * additions of a weight row instead of a pass over the whole board. The running sums of the
 * positions on the current line sit on a stack, so taking a move back is free.
 *
 * Not thread-safe: one evaluator belongs to one search. The (immutable) network is shared.
 */
public final class NnueEvaluator implements Evaluator {

    /** Scores stay well clear of the search's mate scores. */
    private static final int LIMIT = 20_000;

    private final NnueNetwork net;
    private final int hidden;
    /** stack[depth][perspective] is the hidden layer of that view of the board; depth 0 is the root. */
    private short[][][] stack;
    private int top;

    public NnueEvaluator(NnueNetwork net) {
        this.net = net;
        this.hidden = net.hidden();
        this.stack = new short[160][][];
        this.stack[0] = new short[2][hidden];
    }

    @Override
    public void reset(Position pos) {
        top = 0;
        refresh(pos, stack[0]);
    }

    /** Rebuilds both hidden layers from the pieces on the board. */
    void refresh(Position pos, short[][] into) {
        for (int perspective = 0; perspective < 2; perspective++) {
            short[] acc = into[perspective];
            System.arraycopy(net.ftBias(), 0, acc, 0, hidden);
            for (int sq = 0; sq < 64; sq++) {
                int piece = pos.pieceAt(sq);
                if (piece != Position.NO_PIECE) add(acc, perspective, piece, sq);
            }
        }
    }

    @Override
    public void onMake(Position pos, int move) {
        if (top + 1 == stack.length) stack = Arrays.copyOf(stack, stack.length * 2);
        if (stack[top + 1] == null) stack[top + 1] = new short[2][hidden];
        short[][] from = stack[top], to = stack[top + 1];
        top++;

        final int us = pos.sideToMove() ^ 1;                       // the side that just moved
        final int source = Move.from(move), target = Move.to(move), flag = Move.flag(move);
        final int placed = pos.pieceAt(target);                     // what now stands on the target square
        final int moved = Move.isPromotion(move) ? Position.piece(us, Position.PAWN) : placed;
        final int captured = pos.lastCaptured();
        final int captureSquare = flag == Move.EP_CAPTURE ? (us == Position.WHITE ? target - 8 : target + 8) : target;
        final boolean castle = flag == Move.KING_CASTLE || flag == Move.QUEEN_CASTLE;

        // Each case is one pass over the hidden layer: the new sums from the old, a row at a time
        for (int perspective = 0; perspective < 2; perspective++) {
            final short[] before = from[perspective], after = to[perspective];
            if (castle) {
                final boolean kingSide = flag == Move.KING_CASTLE;
                final int rook = Position.piece(us, Position.ROOK);
                final int rookFrom = us == Position.WHITE ? (kingSide ? 7 : 0) : (kingSide ? 63 : 56);
                final int rookTo = us == Position.WHITE ? (kingSide ? 5 : 3) : (kingSide ? 61 : 59);
                shift(after, before, row(perspective, placed, target), row(perspective, rook, rookTo),
                      row(perspective, moved, source), row(perspective, rook, rookFrom));
            } else if (captured != Position.NO_PIECE) {
                shift(after, before, row(perspective, placed, target),
                      row(perspective, moved, source), row(perspective, captured, captureSquare));
            } else {
                shift(after, before, row(perspective, placed, target), row(perspective, moved, source));
            }
        }
    }

    @Override
    public void onUnmake() {
        top--;
    }

    @Override
    public int evaluate(Position pos) {
        final int stm = pos.sideToMove();
        final short[] mine = stack[top][stm], theirs = stack[top][stm ^ 1];
        final short[] w = net.outWeights();
        final int qa = net.qa();
        long sum = 0;
        for (int i = 0; i < hidden; i++) {
            int c = Math.min(Math.max(mine[i], 0), qa);
            sum += (long) (c * c) * w[i];
        }
        for (int i = 0; i < hidden; i++) {
            int c = Math.min(Math.max(theirs[i], 0), qa);
            sum += (long) (c * c) * w[hidden + i];
        }
        long total = sum + (long) net.outBias() * qa;
        long d = (long) qa * qa * net.qb();
        long score = Math.floorDiv(total * net.scale() + d / 2, d);
        return (int) Math.max(-LIMIT, Math.min(LIMIT, score));
    }

    /** The score for {@code pos} computed from scratch, ignoring the running state: for tests. */
    int evaluateFromScratch(Position pos) {
        short[][] scratch = new short[2][hidden];
        refresh(pos, scratch);
        short[][] saved = stack[top];
        stack[top] = scratch;
        try {
            return evaluate(pos);
        } finally {
            stack[top] = saved;
        }
    }

    /** The running hidden layers as they are now, for tests. */
    short[][] current() { return stack[top]; }

    /** The weights of the input for {@code piece} on {@code square}, seen from {@code perspective}. */
    private short[] row(int perspective, int piece, int square) {
        return net.ftRows()[NnueNetwork.feature(perspective, Position.colorOf(piece), Position.typeOf(piece), square)];
    }

    private void add(short[] acc, int perspective, int piece, int square) {
        final short[] w = row(perspective, piece, square);
        for (int i = 0; i < acc.length; i++) acc[i] += w[i];
    }

    // The three shapes of move. Every array is indexed by the same counter, and the loop bound is
    // the array's own length: the form the JIT compiler turns into vector instructions.

    /** after = before + add - sub */
    private static void shift(short[] after, short[] before, short[] add, short[] sub) {
        for (int i = 0; i < after.length; i++) after[i] = (short) (before[i] + add[i] - sub[i]);
    }

    /** after = before + add - sub1 - sub2 */
    private static void shift(short[] after, short[] before, short[] add, short[] sub1, short[] sub2) {
        for (int i = 0; i < after.length; i++) after[i] = (short) (before[i] + add[i] - sub1[i] - sub2[i]);
    }

    /** after = before + add1 + add2 - sub1 - sub2 */
    private static void shift(short[] after, short[] before, short[] add1, short[] add2, short[] sub1, short[] sub2) {
        for (int i = 0; i < after.length; i++) after[i] = (short) (before[i] + add1[i] + add2[i] - sub1[i] - sub2[i]);
    }
}
