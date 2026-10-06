package com.chess.engine.core.eval.nnue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NnueNetwork")
class NnueNetworkTest {

    static byte[] tinyBytes() throws IOException {
        try (InputStream in = NnueNetworkTest.class.getResourceAsStream("/nnue/tiny.nnue")) {
            assertNotNull(in, "test network missing: run training/nnue_fixtures.py");
            return in.readAllBytes();
        }
    }

    @Test
    @DisplayName("loads the file written by the Python exporter")
    void loads() throws IOException {
        NnueNetwork net = NnueNetwork.parse(tinyBytes());

        assertEquals(32, net.hidden());
        assertEquals(1024, net.qa());
        assertEquals(1024, net.qb());
        assertEquals(400, net.scale());
        assertEquals(768, net.ftRows().length);
        assertEquals(32, net.ftRows()[0].length);
        assertEquals(64, net.outWeights().length);
    }

    @Test
    @DisplayName("a file that is not a network is refused")
    void wrongMagic() throws IOException {
        byte[] data = tinyBytes();
        data[0] = 'X';

        assertThrows(IllegalArgumentException.class, () -> NnueNetwork.parse(data));
        assertThrows(IllegalArgumentException.class, () -> NnueNetwork.parse(new byte[10]));
    }

    @Test
    @DisplayName("a damaged file is refused by its checksum")
    void damaged() throws IOException {
        byte[] data = tinyBytes();
        data[200] ^= 0x40;

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> NnueNetwork.parse(data));
        assertTrue(e.getMessage().contains("checksum"), e.getMessage());
    }

    @Test
    @DisplayName("a truncated file is refused")
    void truncated() throws IOException {
        byte[] data = tinyBytes();
        byte[] shorter = java.util.Arrays.copyOf(data, data.length - 100);

        assertThrows(IllegalArgumentException.class, () -> NnueNetwork.parse(shorter));
    }

    @Test
    @DisplayName("an input is a piece of a colour on a square, seen from one side")
    void features() {
        // White's view: its own pieces first, squares as they are
        assertEquals(12, NnueNetwork.feature(0, 0, 0, 12));              // white pawn e2
        assertEquals(384 + 52, NnueNetwork.feature(0, 1, 0, 52));        // black pawn e7
        assertEquals(5 * 64 + 4, NnueNetwork.feature(0, 0, 5, 4));       // white king e1
        // Black's view of the mirrored position is the same numbers: its pawn on e7 is "my pawn on e2"
        assertEquals(12, NnueNetwork.feature(1, 1, 0, 52));
        assertEquals(384 + 52, NnueNetwork.feature(1, 0, 0, 12));   // their pawn on e2 is on e7 from my side
        assertEquals(5 * 64 + 4, NnueNetwork.feature(1, 1, 5, 60));
    }

    @Test
    @DisplayName("every input is a different number from 0 to 767")
    void featuresAreDistinct() {
        boolean[] seen = new boolean[NnueNetwork.FEATURES];
        for (int perspective = 0; perspective < 2; perspective++) {
            java.util.Arrays.fill(seen, false);
            for (int color = 0; color < 2; color++) {
                for (int type = 0; type < 6; type++) {
                    for (int sq = 0; sq < 64; sq++) {
                        int f = NnueNetwork.feature(perspective, color, type, sq);
                        assertFalse(seen[f]);
                        seen[f] = true;
                    }
                }
            }
        }
    }
}
