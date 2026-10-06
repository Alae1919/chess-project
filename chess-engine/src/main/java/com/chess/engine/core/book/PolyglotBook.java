package com.chess.engine.core.book;

import com.chess.engine.core.Move;
import com.chess.engine.core.Position;

import java.io.IOException;
import java.io.InputStream;
import java.util.random.RandomGenerator;

/**
 * An opening book in the Polyglot ".bin" format, which most book tools produce and read.
 * The file is a sorted list of 16-byte entries (position hash, move, weight, learning data),
 * all big-endian; the hash is the same Polyglot key {@link Position#key()} maintains, so a
 * lookup is a binary search.
 */
public final class PolyglotBook {

    private static final int ENTRY_SIZE = 16;
    private static final PolyglotBook EMPTY = new PolyglotBook(new byte[0]);

    private final byte[] data;
    private final int entries;

    public PolyglotBook(byte[] data) {
        this.data = data;
        this.entries = data.length / ENTRY_SIZE;
    }

    public static PolyglotBook empty() { return EMPTY; }

    /** The book stored at {@code path} on the classpath, or an empty book if there is none. */
    public static PolyglotBook fromResource(String path) {
        try (InputStream in = PolyglotBook.class.getResourceAsStream(path)) {
            return in == null ? EMPTY : new PolyglotBook(in.readAllBytes());
        } catch (IOException e) {
            return EMPTY;
        }
    }

    public boolean isEmpty() { return entries == 0; }
    public int size() { return entries; }

    /**
     * A legal move from the book for {@code pos}, chosen at random in proportion to the entries'
     * weights, or {@link Move#NONE} if the position isn't in the book.
     */
    public int pick(Position pos, RandomGenerator random) {
        if (entries == 0) return Move.NONE;
        int first = firstEntryFor(pos.key());
        if (first < 0) return Move.NONE;

        int[] moves = new int[32];
        int[] weights = new int[32];
        int count = 0;
        long total = 0;
        int[] legal = pos.legalMoves();
        for (int i = first; i < entries && count < moves.length && keyAt(i) == pos.key(); i++) {
            int move = match(legal, moveAt(i));
            int weight = weightAt(i);
            if (move == Move.NONE || weight <= 0) continue;
            moves[count] = move;
            weights[count] = weight;
            total += weight;
            count++;
        }
        if (count == 0) return Move.NONE;
        long ticket = random.nextLong(total);
        for (int i = 0; i < count; i++) {
            ticket -= weights[i];
            if (ticket < 0) return moves[i];
        }
        return moves[count - 1];
    }

    // ---- reading entries ------------------------------------------------------------------------

    private long keyAt(int i) {
        long k = 0;
        for (int b = 0; b < 8; b++) k = (k << 8) | (data[i * ENTRY_SIZE + b] & 0xFF);
        return k;
    }

    private int moveAt(int i) {
        return ((data[i * ENTRY_SIZE + 8] & 0xFF) << 8) | (data[i * ENTRY_SIZE + 9] & 0xFF);
    }

    private int weightAt(int i) {
        return ((data[i * ENTRY_SIZE + 10] & 0xFF) << 8) | (data[i * ENTRY_SIZE + 11] & 0xFF);
    }

    /** Index of the first entry with {@code key}, or -1. Keys are unsigned 64-bit numbers. */
    private int firstEntryFor(long key) {
        int low = 0, high = entries - 1, found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = Long.compareUnsigned(keyAt(mid), key);
            if (cmp < 0) {
                low = mid + 1;
            } else {
                if (cmp == 0) found = mid;
                high = mid - 1;
            }
        }
        return found;
    }

    /**
     * Finds the legal move a book move stands for. The book writes a move as to-file, to-row,
     * from-file, from-row and promotion piece, and castling as the king taking its own rook
     * (e1h1), which is turned into the king's real destination.
     */
    private static int match(int[] legal, int bookMove) {
        int toFile = bookMove & 7, toRow = (bookMove >> 3) & 7;
        int fromFile = (bookMove >> 6) & 7, fromRow = (bookMove >> 9) & 7;
        int promotion = (bookMove >> 12) & 7;                      // 0 none, 1 knight .. 4 queen
        int from = fromRow * 8 + fromFile, to = toRow * 8 + toFile;
        for (int move : legal) {
            if (Move.from(move) != from) continue;
            // castling is written king-takes-rook: e1h1 / e1a1 / e8h8 / e8a8
            int bookTo = Move.isCastle(move)
                ? (Move.flag(move) == Move.KING_CASTLE ? from + 3 : from - 4)
                : Move.to(move);
            if (bookTo != to) continue;
            int promo = Move.isPromotion(move) ? Move.promotionType(move) : 0;
            if (promo == promotion) return move;
        }
        return Move.NONE;
    }
}
