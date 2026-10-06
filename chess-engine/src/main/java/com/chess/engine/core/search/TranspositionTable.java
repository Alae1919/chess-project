package com.chess.engine.core.search;

import java.util.Arrays;

/**
 * Remembers what the search already worked out about a position, keyed by its hash, so a
 * position reached by another move order is not searched again and the best move found at a
 * shallow depth can be tried first at the next.
 *
 * An entry is two longs: the full key (to be sure it is the same position) and the data packed
 * as: move (16 bits), score (16), depth (8), bound (2), age (6). One search owns one table.
 */
public final class TranspositionTable {

    public static final int EXACT = 0, LOWER = 1, UPPER = 2;

    private long[] keys;
    private long[] data;
    private int mask;
    private int age;

    /** A table of about {@code megabytes} megabytes (16 bytes an entry, rounded down to a power of two). */
    public TranspositionTable(int megabytes) {
        long wanted = Math.max(1, megabytes) * 1024L * 1024L / 16;
        int entries = (int) Math.min(1 << 28, Long.highestOneBit(wanted));
        keys = new long[entries];
        data = new long[entries];
        mask = entries - 1;
    }

    public int capacity() { return keys.length; }

    public void clear() {
        Arrays.fill(keys, 0L);
        Arrays.fill(data, 0L);
        age = 0;
    }

    /** Marks the start of a new search: older entries become the first to be replaced. */
    public void newSearch() { age = (age + 1) & 63; }

    /** The packed entry for {@code key}, or 0 if there is none. Read it with the accessors below. */
    public long probe(long key) {
        int i = (int) key & mask;
        return keys[i] == key ? data[i] : 0;
    }

    public static int move(long entry) { return (int) (entry & 0xFFFF); }
    public static int score(long entry) { return (short) (entry >>> 16); }
    public static int depth(long entry) { return (int) ((entry >>> 32) & 0xFF); }
    public static int bound(long entry) { return (int) ((entry >>> 40) & 3); }
    private static int ageOf(long entry) { return (int) ((entry >>> 42) & 63); }

    public void store(long key, int move, int score, int depth, int bound) {
        int i = (int) key & mask;
        long old = data[i];
        boolean sameKey = keys[i] == key;
        if (old != 0 && !sameKey && ageOf(old) == age && depth < depth(old)) return;   // keep the deeper, current entry
        if (move == 0 && sameKey) move = move(old);                                   // don't lose a good move to a fail-low
        long packed = (move & 0xFFFFL)
                    | ((long) (score & 0xFFFF) << 16)
                    | ((long) (Math.min(depth, 255)) << 32)
                    | ((long) bound << 40)
                    | ((long) age << 42)
                    | (1L << 63);                                                     // never zero, so 0 can mean "no entry"
        keys[i] = key;
        data[i] = packed;
    }

    /** Roughly how full the table is, in permille, from a sample: for UCI's "hashfull". */
    public int fullPermille() {
        int sample = Math.min(1000, keys.length), used = 0;
        for (int i = 0; i < sample; i++) if (data[i] != 0 && ageOf(data[i]) == age) used++;
        return used * 1000 / sample;
    }
}
