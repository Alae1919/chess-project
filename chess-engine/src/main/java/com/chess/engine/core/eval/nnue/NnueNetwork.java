package com.chess.engine.core.eval.nnue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * The weights of a trained evaluation network, loaded from an {@code .nnue} file (the format is
 * described in training/export.py). Immutable, so one instance is shared by every game and
 * every search; the running state lives in {@link NnueEvaluator}.
 *
 * The network has 768 inputs (a piece of a colour on a square, seen from one side's point of
 * view, see {@link #feature}), {@link #hidden()} hidden units shared by both views, and one
 * output. The weights are integers: the hidden layer scaled by {@link #qa()} and the output
 * layer by {@link #qb()}.
 */
public final class NnueNetwork {

    public static final int FEATURES = 768;
    private static final byte[] MAGIC = {'R', 'X', 'N', 'N'};
    private static final int VERSION = 1;
    /** A position has at most 32 pieces, so a hidden sum adds at most this many weights. */
    private static final int MAX_PIECES = 32;

    private final int hidden, qa, qb, scale;
    /**
     * One row of {@code hidden} weights per input, each its own array: the loops that add and subtract
     * rows then index every array with the same counter, which is what lets the JIT compiler turn them
     * into vector instructions (about six times faster than indexing into one big array).
     */
    private final short[][] ftRows;
    private final short[] ftBias;
    /** The side to move's {@code hidden} weights first, then the other side's. */
    private final short[] outWeights;
    private final int outBias;

    private NnueNetwork(int hidden, int qa, int qb, int scale, short[][] ftRows, short[] ftBias, short[] outWeights, int outBias) {
        this.hidden = hidden; this.qa = qa; this.qb = qb; this.scale = scale;
        this.ftRows = ftRows; this.ftBias = ftBias; this.outWeights = outWeights; this.outBias = outBias;
    }

    public int hidden() { return hidden; }
    public int qa() { return qa; }
    public int qb() { return qb; }
    public int scale() { return scale; }
    short[][] ftRows() { return ftRows; }
    short[] ftBias() { return ftBias; }
    short[] outWeights() { return outWeights; }
    int outBias() { return outBias; }

    /**
     * The input for a piece on a square, from one side's point of view: that side's own pieces come
     * first, and the board is turned over for Black, so the network learns "my pieces" and "their
     * pieces" once whichever colour it plays.
     *
     * @param perspective 0 = White's view, 1 = Black's
     * @param color       the piece's colour
     * @param type        pawn 0 .. king 5
     */
    public static int feature(int perspective, int color, int type, int square) {
        return (color != perspective ? 384 : 0) + type * 64 + (perspective == 0 ? square : square ^ 56);
    }

    // ---- loading -------------------------------------------------------------------------------------

    public static NnueNetwork load(Path file) throws IOException {
        return parse(Files.readAllBytes(file));
    }

    public static NnueNetwork load(InputStream in) throws IOException {
        return parse(in.readAllBytes());
    }

    /** The network bundled with the application (nnue/default.nnue on the classpath), or null if there is none. */
    public static NnueNetwork loadBundled() throws IOException {
        try (InputStream in = NnueNetwork.class.getResourceAsStream("/nnue/default.nnue")) {
            return in == null ? null : load(in);
        }
    }

    static NnueNetwork parse(byte[] data) {
        if (data.length < 32) throw new IllegalArgumentException("not a network file: too short");
        for (int i = 0; i < 4; i++) {
            if (data[i] != MAGIC[i]) throw new IllegalArgumentException("not a network file: wrong magic");
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length - 4);
        if (crc.getValue() != Integer.toUnsignedLong(buf.getInt(data.length - 4))) {
            throw new IllegalArgumentException("network file is damaged: checksum mismatch");
        }

        buf.position(4);
        int version = buf.getInt(), features = buf.getInt(), hidden = buf.getInt();
        int qa = buf.getInt(), qb = buf.getInt(), scale = buf.getInt();
        if (version != VERSION) throw new IllegalArgumentException("unsupported network version " + version);
        if (features != FEATURES) throw new IllegalArgumentException("unsupported network: " + features + " inputs");
        if (hidden < 1 || hidden > 4096 || qa < 1 || qb < 1 || scale < 1) {
            throw new IllegalArgumentException("network header is not sensible");
        }
        long expected = 4 + 6 * 4 + 2L * FEATURES * hidden + 2L * hidden + 2L * 2 * hidden + 4 + 4;
        if (expected != data.length) {
            throw new IllegalArgumentException("network file has " + data.length + " bytes, expected " + expected);
        }

        short[][] ftRows = new short[FEATURES][hidden];
        for (short[] row : ftRows) {
            buf.asShortBuffer().get(row);
            buf.position(buf.position() + 2 * hidden);
        }
        short[] ftBias = new short[hidden];
        buf.asShortBuffer().get(ftBias);
        buf.position(buf.position() + 2 * hidden);
        short[] outWeights = new short[2 * hidden];
        buf.asShortBuffer().get(outWeights);
        buf.position(buf.position() + 2 * outWeights.length);
        int outBias = buf.getInt();

        checkSumsFit(hidden, ftRows, ftBias);
        return new NnueNetwork(hidden, qa, qb, scale, ftRows, ftBias, outWeights, outBias);
    }

    /**
     * The evaluator keeps each hidden sum in 16 bits, so refuse a network whose sums could leave
     * that range for some position, however unlikely: the worst case is the bias plus the largest
     * weights of 32 pieces.
     */
    private static void checkSumsFit(int hidden, short[][] rows, short[] bias) {
        int[] column = new int[FEATURES];
        for (int h = 0; h < hidden; h++) {
            for (int f = 0; f < FEATURES; f++) column[f] = Math.abs(rows[f][h]);
            Arrays.sort(column);
            long worst = Math.abs((long) bias[h]);
            for (int i = 0; i < MAX_PIECES; i++) worst += column[FEATURES - 1 - i];
            if (worst > Short.MAX_VALUE) {
                throw new IllegalArgumentException("network is unsafe: hidden unit " + h + " could overflow 16 bits");
            }
        }
    }

}
