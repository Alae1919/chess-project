package com.chess.engine.core.book;

import com.chess.engine.core.Bits;
import com.chess.engine.core.Move;
import com.chess.engine.core.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PolyglotBook")
class PolyglotBookTest {

    private record Entry(long key, int move, int weight) { }

    /** Encodes a move the way a book file does. */
    private static int bookMove(String from, String to, int promotion) {
        int f = Bits.parse(from), t = Bits.parse(to);
        return Bits.file(t) | (Bits.rank(t) << 3) | (Bits.file(f) << 6) | (Bits.rank(f) << 9) | (promotion << 12);
    }

    private static PolyglotBook book(Entry... entries) {
        List<Entry> sorted = new ArrayList<>(List.of(entries));
        sorted.sort(Comparator.comparing(Entry::key, Long::compareUnsigned));      // a book file is sorted by key
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Entry e : sorted) {
            for (int b = 7; b >= 0; b--) out.write((int) (e.key() >>> (8 * b)) & 0xFF);
            out.write((e.move() >> 8) & 0xFF);
            out.write(e.move() & 0xFF);
            out.write((e.weight() >> 8) & 0xFF);
            out.write(e.weight() & 0xFF);
            out.write(0); out.write(0); out.write(0); out.write(0);                // learning data, unused
        }
        return new PolyglotBook(out.toByteArray());
    }

    private static String pick(PolyglotBook b, Position pos) {
        return Move.toUci(b.pick(pos, new Random(1)));
    }

    @Test
    @DisplayName("an empty book knows nothing")
    void emptyBook() {
        assertEquals(Move.NONE, PolyglotBook.empty().pick(Position.startPosition(), new Random(1)));
        assertTrue(PolyglotBook.empty().isEmpty());
        assertTrue(PolyglotBook.fromResource("/no/such/book.bin").isEmpty());
    }

    @Test
    @DisplayName("finds the moves for a position, in proportion to their weights")
    void picksByWeight() {
        Position start = Position.startPosition();
        PolyglotBook b = book(
            new Entry(start.key(), bookMove("e2", "e4", 0), 3),
            new Entry(start.key(), bookMove("d2", "d4", 0), 1),
            new Entry(0x1234L, bookMove("a2", "a3", 0), 5));       // another position

        Random random = new Random(7);
        int e4 = 0, d4 = 0;
        for (int i = 0; i < 4_000; i++) {
            String uci = Move.toUci(b.pick(start, random));
            if (uci.equals("e2e4")) e4++;
            else if (uci.equals("d2d4")) d4++;
            else fail("unexpected " + uci);
        }
        assertEquals(4_000, e4 + d4);
        assertTrue(Math.abs(e4 / 4000.0 - 0.75) < 0.04, "e4 chosen " + e4 + " of 4000");
    }

    @Test
    @DisplayName("a position that is not in the book gets no move")
    void unknownPosition() {
        Position start = Position.startPosition();
        PolyglotBook b = book(new Entry(start.key() ^ 1, bookMove("e2", "e4", 0), 1));

        assertEquals(Move.NONE, b.pick(start, new Random(1)));
    }

    @Test
    @DisplayName("keys are compared as unsigned numbers, as the format sorts them")
    void unsignedKeys() {
        // after 1.e4 the Polyglot key is 0x823C9B50FD114196: its top bit is set, so it is negative as a long
        Position afterE4 = Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
        assertEquals("823c9b50fd114196", Long.toHexString(afterE4.key()));
        assertTrue(afterE4.key() < 0);
        PolyglotBook b = book(
            new Entry(5L, bookMove("a2", "a3", 0), 1),
            new Entry(afterE4.key(), bookMove("g8", "f6", 0), 1),
            new Entry(Long.MAX_VALUE, bookMove("a2", "a4", 0), 1));

        assertEquals("g8f6", pick(b, afterE4));
    }

    @Test
    @DisplayName("castling, written king-takes-rook in the file, becomes the king's move")
    void castling() {
        Position white = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertEquals("e1g1", pick(book(new Entry(white.key(), bookMove("e1", "h1", 0), 1)), white));
        assertEquals("e1c1", pick(book(new Entry(white.key(), bookMove("e1", "a1", 0), 1)), white));

        Position black = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1");
        assertEquals("e8g8", pick(book(new Entry(black.key(), bookMove("e8", "h8", 0), 1)), black));
        assertEquals("e8c8", pick(book(new Entry(black.key(), bookMove("e8", "a8", 0), 1)), black));
    }

    @Test
    @DisplayName("a promotion names its piece")
    void promotion() {
        Position pos = Position.fromFen("8/P7/8/8/8/8/8/k6K w - - 0 1");

        assertEquals("a7a8q", pick(book(new Entry(pos.key(), bookMove("a7", "a8", 4), 1)), pos));
        assertEquals("a7a8n", pick(book(new Entry(pos.key(), bookMove("a7", "a8", 1), 1)), pos));
    }

    @Test
    @DisplayName("an entry for a move that is not legal is ignored")
    void illegalEntriesAreIgnored() {
        Position start = Position.startPosition();
        PolyglotBook b = book(new Entry(start.key(), bookMove("e2", "e5", 0), 9));

        assertEquals(Move.NONE, b.pick(start, new Random(1)));
    }

    @Test
    @DisplayName("the bundled book, built from strong players' games, knows the usual first moves and the replies to them")
    void bundledBook() {
        PolyglotBook bundled = PolyglotBook.fromResource("/openings/book.bin");
        assertTrue(bundled.size() > 5_000, "book size " + bundled.size());

        // from the start, over many draws, the common first moves come up and nothing odd does
        Random random = new Random(3);
        java.util.Set<String> firstMoves = new java.util.HashSet<>();
        for (int i = 0; i < 300; i++) firstMoves.add(pick(bundled, Position.startPosition(), random));
        assertTrue(firstMoves.contains("e2e4") && firstMoves.contains("d2d4"), firstMoves.toString());
        assertTrue(java.util.Set.of("e2e4", "d2d4", "g1f3", "c2c4", "b1c3", "g2g3", "b2b3", "f2f4").containsAll(firstMoves), firstMoves.toString());

        // and a reply to 1.e4 is a legal move
        Position afterE4 = Position.startPosition();
        afterE4.makeMove(afterE4.parseUci("e2e4"));
        int reply = bundled.pick(afterE4, new Random(4));
        assertNotEquals(Move.NONE, reply);
        assertTrue(java.util.Arrays.stream(afterE4.legalMoves()).anyMatch(m -> m == reply));
    }

    @Test
    @DisplayName("every move in the bundled book is legal in its position when played out along its main line")
    void bundledBookMainLine() {
        PolyglotBook bundled = PolyglotBook.fromResource("/openings/book.bin");
        Position pos = Position.startPosition();
        Random random = new Random(9);
        int plies = 0;
        for (; plies < 12; plies++) {
            int move = bundled.pick(pos, random);
            if (move == Move.NONE) break;
            assertTrue(java.util.Arrays.stream(pos.legalMoves()).anyMatch(m -> m == move), pos.toFen());
            pos.makeMove(move);
        }
        assertTrue(plies >= 8, "the book should carry a game at least eight plies: " + plies);
    }

    private static String pick(PolyglotBook b, Position pos, Random random) {
        return Move.toUci(b.pick(pos, random));
    }
}
