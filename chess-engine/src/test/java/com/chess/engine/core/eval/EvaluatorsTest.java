package com.chess.engine.core.eval;

import com.chess.engine.core.eval.nnue.NnueEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Evaluators")
class EvaluatorsTest {

    @AfterEach
    void restoreDefaults() {
        Evaluators.configure(Evaluators.Mode.NNUE, null);
    }

    private static Path tinyNetwork() throws URISyntaxException {
        return Path.of(EvaluatorsTest.class.getResource("/nnue/tiny.nnue").toURI());
    }

    @Test
    @DisplayName("classical mode makes the classical evaluator")
    void classical() {
        String described = Evaluators.configure(Evaluators.Mode.CLASSICAL, null);

        assertInstanceOf(ClassicalEvaluator.class, Evaluators.create());
        assertEquals("classical evaluation", described);
    }

    @Test
    @DisplayName("a network file makes the neural evaluator")
    void networkFile() throws Exception {
        String described = Evaluators.configure(Evaluators.Mode.NNUE, tinyNetwork());

        assertInstanceOf(NnueEvaluator.class, Evaluators.create());
        assertTrue(described.contains("neural network") && described.contains("32 hidden"), described);
    }

    @Test
    @DisplayName("each call makes an evaluator of its own, so searches do not share running state")
    void separateEvaluators() throws Exception {
        Evaluators.configure(Evaluators.Mode.NNUE, tinyNetwork());

        assertNotSame(Evaluators.create(), Evaluators.create());
    }

    @Test
    @DisplayName("a network file that is missing is reported, and the engine still has an evaluation")
    void missingFile() {
        String described = Evaluators.configure(Evaluators.Mode.NNUE, Path.of("no-such-network.nnue"));

        assertTrue(described.contains("could not load network"), described);
        assertNotNull(Evaluators.create());
    }

    @Test
    @DisplayName("a network file that is damaged is reported, and the engine still has an evaluation")
    void damagedFile() throws Exception {
        Path bad = java.nio.file.Files.createTempFile("bad", ".nnue");
        try {
            java.nio.file.Files.write(bad, new byte[100]);
            String described = Evaluators.configure(Evaluators.Mode.NNUE, bad);

            assertTrue(described.contains("could not load network"), described);
            assertNotNull(Evaluators.create());
        } finally {
            java.nio.file.Files.deleteIfExists(bad);
        }
    }
}
