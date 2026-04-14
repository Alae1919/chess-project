package com.chess.engine.opening;

import com.chess.domain.board.Board;
import com.chess.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Polyglot opening book reader.
 *
 * A Polyglot .bin file is a sorted array of 16-byte entries:
 *   [8 bytes Zobrist key][2 bytes move][2 bytes weight][4 bytes learn]
 *
 * Lookup is a binary search over sorted keys — O(log n).
 * The entire file is loaded into a byte[] at construction for fast access.
 *
 * If the book file is absent, {@link #isAvailable()} returns false and
 * {@link #lookup(Board)} always returns empty — the AI falls back to search.
 *
 * Book file: classpath:/openings/book.bin
 * Recommended source: any free Polyglot book (e.g. gm2001.bin, Titans.bin).
 */
public final class OpeningBook {

    private static final Logger log = LoggerFactory.getLogger(OpeningBook.class);

    private static final int ENTRY_SIZE = 16;     // bytes per entry
    private static final int MOVE_OFFSET = 8;     // offset of the 2-byte move field
    private static final int WEIGHT_OFFSET = 10;  // offset of the 2-byte weight field

    private final byte[]  data;        // raw file bytes (null when book unavailable)
    private final boolean available;
    private final Random  rng = new Random();

    private OpeningBook(byte[] data) {
        this.data      = data;
        this.available = (data != null && data.length >= ENTRY_SIZE);
    }

    /**
     * Loads the book from {@code classpath:/openings/book.bin}.
     * Silently returns a disabled book if the resource is absent.
     */
    public static OpeningBook load() {
        try (InputStream in = OpeningBook.class
                .getResourceAsStream("/openings/book.bin")) {
            if (in == null) {
                log.info("Opening book not found at classpath:/openings/book.bin — " +
                         "AI will use search for all moves. " +
                         "Drop any Polyglot .bin book there to enable it.");
                return new OpeningBook(null);
            }
            byte[] bytes = in.readAllBytes();
            log.info("Opening book loaded: {} entries", bytes.length / ENTRY_SIZE);
            return new OpeningBook(bytes);
        } catch (Exception e) {
            log.warn("Failed to load opening book: {}", e.getMessage());
            return new OpeningBook(null);
        }
    }

    public boolean isAvailable() { return available; }

    /**
     * Returns a move from the book for the given position, chosen
     * weighted-randomly among all book entries for this hash.
     *
     * @return a legal move from the book, or empty if this position is not in the book
     *         or the decoded move does not match any legal move.
     */
    public Optional<Move> lookup(Board board) {
        if (!available) return Optional.empty();

        long key = PolyglotKey.compute(board);
        List<BookEntry> entries = findEntries(key);
        if (entries.isEmpty()) return Optional.empty();

        // Weighted random selection
        BookEntry chosen = weightedRandom(entries);

        // Decode Polyglot move to our Move model
        List<Move> legal = com.chess.domain.rules.MoveGenerator.generateLegalMoves(board);
        return decodeMove(chosen.encodedMove, board, legal);
    }

    // -----------------------------------------------------------------------
    // Binary search for all entries matching the given key
    // -----------------------------------------------------------------------

    private List<BookEntry> findEntries(long key) {
        int lo = 0;
        int hi = data.length / ENTRY_SIZE - 1;

        // Binary search for the first entry with this key
        int first = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long midKey = readKey(mid);
            if (midKey == key)      { first = mid; hi = mid - 1; }
            else if (midKey < key)   lo = mid + 1;
            else                     hi = mid - 1;
        }

        if (first == -1) return List.of();

        List<BookEntry> result = new ArrayList<>();
        int total = data.length / ENTRY_SIZE;
        for (int i = first; i < total && readKey(i) == key; i++) {
            int encoded = readShort(i * ENTRY_SIZE + MOVE_OFFSET);
            int weight  = readShort(i * ENTRY_SIZE + WEIGHT_OFFSET);
            if (weight > 0) result.add(new BookEntry(encoded, weight));
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Weighted random selection
    // -----------------------------------------------------------------------

    private BookEntry weightedRandom(List<BookEntry> entries) {
        int totalWeight = entries.stream().mapToInt(e -> e.weight).sum();
        int pick = rng.nextInt(totalWeight);
        int cumulative = 0;
        for (BookEntry e : entries) {
            cumulative += e.weight;
            if (pick < cumulative) return e;
        }
        return entries.get(entries.size() - 1); // fallback
    }

    // -----------------------------------------------------------------------
    // Polyglot move decoding
    // -----------------------------------------------------------------------

    /**
     * Decodes a Polyglot-encoded move and matches it against the list of legal moves.
     *
     * Polyglot encodes a move as 16 bits:
     *   bits 0-2   to-file
     *   bits 3-5   to-rank
     *   bits 6-8   from-file
     *   bits 9-11  from-rank
     *   bits 12-14 promotion piece (0=none, 1=knight, 2=bishop, 3=rook, 4=queen)
     */
    private Optional<Move> decodeMove(int encoded, Board board, List<Move> legal) {
        int toFile   = (encoded)       & 0x7;
        int toRank   = (encoded >> 3)  & 0x7;
        int fromFile = (encoded >> 6)  & 0x7;
        int fromRank = (encoded >> 9)  & 0x7;
        int promoBits = (encoded >> 12) & 0x7;

        PieceType promo = switch (promoBits) {
            case 1 -> PieceType.KNIGHT;
            case 2 -> PieceType.BISHOP;
            case 3 -> PieceType.ROOK;
            case 4 -> PieceType.QUEEN;
            default -> null;
        };

        // Match against legal moves by from/to square (and promotion type if any)
        for (Move m : legal) {
            if (m.from().file() == fromFile && m.from().rank() == fromRank
                    && m.to().file() == toFile && m.to().rank() == toRank
                    && m.promotion() == promo) {
                return Optional.of(m);
            }
        }
        return Optional.empty(); // book move not legal in current position
    }

    // -----------------------------------------------------------------------
    // Raw byte readers (big-endian, as per Polyglot spec)
    // -----------------------------------------------------------------------

    private long readKey(int entryIndex) {
        return ByteBuffer.wrap(data, entryIndex * ENTRY_SIZE, 8)
                         .order(ByteOrder.BIG_ENDIAN)
                         .getLong();
    }

    private int readShort(int byteOffset) {
        return ByteBuffer.wrap(data, byteOffset, 2)
                         .order(ByteOrder.BIG_ENDIAN)
                         .getShort() & 0xFFFF;
    }

    // -----------------------------------------------------------------------

    private record BookEntry(int encodedMove, int weight) {}
}
