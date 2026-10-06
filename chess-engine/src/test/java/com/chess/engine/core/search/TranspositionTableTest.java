package com.chess.engine.core.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TranspositionTable")
class TranspositionTableTest {

    @Test
    @DisplayName("stores and returns what was saved, including negative scores")
    void roundTrip() {
        TranspositionTable tt = new TranspositionTable(1);

        tt.store(0xABCDEF1234L, 0x1234, -321, 9, TranspositionTable.UPPER);
        long e = tt.probe(0xABCDEF1234L);

        assertNotEquals(0, e);
        assertEquals(0x1234, TranspositionTable.move(e));
        assertEquals(-321, TranspositionTable.score(e));
        assertEquals(9, TranspositionTable.depth(e));
        assertEquals(TranspositionTable.UPPER, TranspositionTable.bound(e));
    }

    @Test
    @DisplayName("an unknown position, or one sharing a slot, is not found")
    void missesWhenAbsent() {
        TranspositionTable tt = new TranspositionTable(1);
        tt.store(5L, 1, 10, 3, TranspositionTable.EXACT);

        assertEquals(0, tt.probe(6L));
        assertEquals(0, tt.probe(5L + tt.capacity()), "same slot, different position");
    }

    @Test
    @DisplayName("a deeper entry in the same search is not overwritten by a shallower one for another position")
    void keepsDeeperEntries() {
        TranspositionTable tt = new TranspositionTable(1);
        long other = 7L + tt.capacity();
        tt.store(7L, 11, 50, 12, TranspositionTable.EXACT);

        tt.store(other, 22, 60, 3, TranspositionTable.EXACT);

        assertNotEquals(0, tt.probe(7L), "the deep entry stays");
        assertEquals(0, tt.probe(other));
    }

    @Test
    @DisplayName("entries from an earlier search give way")
    void oldEntriesAreReplaced() {
        TranspositionTable tt = new TranspositionTable(1);
        long other = 7L + tt.capacity();
        tt.store(7L, 11, 50, 12, TranspositionTable.EXACT);
        tt.newSearch();

        tt.store(other, 22, 60, 3, TranspositionTable.EXACT);

        assertNotEquals(0, tt.probe(other));
    }

    @Test
    @DisplayName("an update with no move keeps the move already known for that position")
    void keepsTheKnownMove() {
        TranspositionTable tt = new TranspositionTable(1);
        tt.store(9L, 77, 5, 4, TranspositionTable.LOWER);

        tt.store(9L, 0, -5, 6, TranspositionTable.UPPER);

        assertEquals(77, TranspositionTable.move(tt.probe(9L)));
        assertEquals(6, TranspositionTable.depth(tt.probe(9L)));
    }

    @Test
    @DisplayName("clearing forgets everything")
    void clear() {
        TranspositionTable tt = new TranspositionTable(1);
        tt.store(9L, 77, 5, 4, TranspositionTable.LOWER);

        tt.clear();

        assertEquals(0, tt.probe(9L));
    }
}
