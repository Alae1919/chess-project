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

        for (int perspective = 0; perspective < 2; perspective++) {
            short[] acc = to[perspective];
            System.arraycopy(from[perspective], 0, acc, 0, hidden);
            subtract(acc, perspective, moved, source);
            add(acc, perspective, placed, target);
            if (captured != Position.NO_PIECE) subtract(acc, perspective, captured, captureSquare);
            if (flag == Move.KING_CASTLE) {
                int rook = Position.piece(us, Position.ROOK);
                subtract(acc, perspective, rook, us == Position.WHITE ? 7 : 63);
                add(acc, perspective, rook, us == Position.WHITE ? 5 : 61);
            } else if (flag == Move.QUEEN_CASTLE) {
                int rook = Position.piece(us, Position.ROOK);
                subtract(acc, perspective, rook, us == Position.WHITE ? 0 : 56);
                add(acc, perspective, rook, us == Position.WHITE ? 3 : 59);
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

    private void add(short[] acc, int perspective, int piece, int square) {
        int row = NnueNetwork.feature(perspective, Position.colorOf(piece), Position.typeOf(piece), square) * hidden;
        short[] w = net.ftWeights();
        for (int i = 0; i < hidden; i++) acc[i] += w[row + i];
    }

    private void subtract(short[] acc, int perspective, int piece, int square) {
        int row = NnueNetwork.feature(perspective, Position.colorOf(piece), Position.typeOf(piece), square) * hidden;
        short[] w = net.ftWeights();
        for (int i = 0; i < hidden; i++) acc[i] -= w[row + i];
    }
}
