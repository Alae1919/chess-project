package com.chess.engine.core.eval;

import com.chess.engine.core.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClassicalEvaluator")
class ClassicalEvaluatorTest {

    private final ClassicalEvaluator eval = new ClassicalEvaluator();

    private int score(String fen) { return eval.evaluate(Position.fromFen(fen)); }

    /** The same position with the board flipped top to bottom and the colours swapped. */
    static String mirror(String fen) {
        String[] f = fen.split(" ");
        String[] ranks = f[0].split("/");
        StringBuilder placement = new StringBuilder();
        for (int i = 7; i >= 0; i--) {
            for (char c : ranks[i].toCharArray()) {
                placement.append(Character.isUpperCase(c) ? Character.toLowerCase(c)
                                 : Character.isLowerCase(c) ? Character.toUpperCase(c) : c);
            }
            if (i > 0) placement.append('/');
        }
        String side = f[1].equals("w") ? "b" : "w";
        StringBuilder castling = new StringBuilder();
        for (char c : f[2].toCharArray()) {
            castling.append(c == '-' ? c : Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        if (castling.length() > 1) {            // keep the usual KQkq order
            char[] order = "KQkq".toCharArray();
            StringBuilder sorted = new StringBuilder();
            for (char o : order) if (castling.indexOf(String.valueOf(o)) >= 0) sorted.append(o);
            castling = sorted;
        }
        String ep = f[3].equals("-") ? "-" : "" + f[3].charAt(0) + (char) ('1' + ('8' - f[3].charAt(1)));
        return placement + " " + side + " " + castling + " " + ep + " " + f[4] + " " + f[5];
    }

    private static List<String> fixtureFens() throws Exception {
        List<String> fens = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                ClassicalEvaluatorTest.class.getResourceAsStream("/engine/polyglot-keys.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) fens.add(line.substring(0, line.lastIndexOf(',')));
        }
        return fens;
    }

    @Test
    @DisplayName("a position and its mirror image score the same for the side to move, for 400 positions")
    void mirrorSymmetry() throws Exception {
        for (String fen : fixtureFens()) {
            assertEquals(score(fen), score(mirror(fen)), "mirror of " + fen);
        }
    }

    @Test
    @DisplayName("the starting position is level apart from the move")
    void startPositionIsLevel() {
        int s = score(Position.START_FEN);
        assertTrue(s >= 0 && s < 30, "score " + s);
    }

    @Test
    @DisplayName("extra material counts for the side that has it, whoever is to move")
    void materialCounts() {
        assertTrue(score("4k3/8/8/8/8/8/8/3QK3 w - - 0 1") > 800);
        assertTrue(score("4k3/8/8/8/8/8/8/3QK3 b - - 0 1") < -800);
        assertTrue(score("3qk3/8/8/8/8/8/8/4K3 b - - 0 1") > 800);
    }

    @Test
    @DisplayName("pawns are better advanced: the tables are read the right way up")
    void pawnsAreWorthMoreWhenAdvanced() {
        int advanced = score("4k3/8/8/8/4P3/8/8/4K3 w - - 0 1");
        int atHome = score("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1");
        assertTrue(advanced > atHome, advanced + " should beat " + atHome);

        int blackAdvanced = score("4k3/8/8/4p3/8/8/8/4K3 b - - 0 1");
        int blackAtHome = score("4k3/4p3/8/8/8/8/8/4K3 b - - 0 1");
        assertTrue(blackAdvanced > blackAtHome, "and the same for Black");
    }

    @Test
    @DisplayName("a passed pawn is worth more than one that can be stopped")
    void passedPawns() {
        int passed = score("4k3/8/8/4P3/8/8/8/4K3 w - - 0 1");
        int blocked = score("4k3/3p1p2/8/4P3/8/8/8/4K3 w - - 0 1") - 2 * 100;   // compare ignoring the two extra pawns' material
        assertTrue(passed > blocked, "passed " + passed + " vs held " + blocked);
    }

    @Test
    @DisplayName("a king sheltered by its pawns is safer than one without them")
    void kingShelter() {
        int sheltered = score("4k3/8/8/8/8/8/5PPP/6K1 w - - 0 1");
        int exposed = score("4k3/8/8/8/8/8/8/6K1 w - - 0 1") + 3 * 100;        // the same material, no shield
        assertTrue(sheltered > exposed, sheltered + " vs " + exposed);
    }

    @Test
    @DisplayName("bare kings and a lone minor piece are a draw")
    void drawnMaterial() {
        assertEquals(0, score("4k3/8/8/8/8/8/8/4K3 w - - 0 1"));
        assertEquals(0, score("4k3/8/8/8/8/8/8/3NK3 w - - 0 1"));
    }

    @Test
    @DisplayName("in a won ending the winner is encouraged to push the lone king to the edge")
    void mopUp() {
        int kingInCentre = score("8/8/8/3k4/8/8/8/R3K3 w - - 0 1");
        int kingInCorner = score("7k/8/8/8/8/8/8/R3K3 w - - 0 1");
        assertTrue(kingInCorner > kingInCentre, kingInCorner + " vs " + kingInCentre);
    }
}
