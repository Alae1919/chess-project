package com.chess.infrastructure.api;

import com.chess.ChessApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GameController.
 *
 * Uses @SpringBootTest to load the full application context and MockMvc to
 * fire HTTP requests without starting a real server.
 *
 * Test order: each nested class is independent (no shared game state).
 */
@SpringBootTest(classes = ChessApplication.class)
@AutoConfigureMockMvc
@DisplayName("GameController Integration Tests")
class GameControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    // ----------------------------------------------------------------
    // Helper: create a game and return its ID
    // ----------------------------------------------------------------

    private String createGame(String aiColor) throws Exception {
        String body = json.writeValueAsString(Map.of("aiColor", aiColor));
        MvcResult result = mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return json.readTree(result.getResponse().getContentAsString())
            .get("gameId").asText();
    }

    private String createGameDefault() throws Exception {
        return createGame("BLACK");   // default: AI is BLACK
    }

    // ================================================================
    // POST /api/games
    // ================================================================

    @Nested
    @DisplayName("POST /api/games — create game")
    class CreateGame {

        @Test
        @DisplayName("creates a game with default settings and returns 201")
        void createDefault() throws Exception {
            mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").isNotEmpty())
                .andExpect(jsonPath("$.activeColor").value("WHITE"))
                .andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.legalMoves", hasSize(20)))
                .andExpect(jsonPath("$.moveHistory", hasSize(0)))
                .andExpect(jsonPath("$.lastMove").doesNotExist());
        }

        @Test
        @DisplayName("creates a game with no request body (all defaults)")
        void createNoBody() throws Exception {
            mvc.perform(post("/api/games"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").isNotEmpty());
        }

        @Test
        @DisplayName("creates a game from a custom FEN")
        void createFromFen() throws Exception {
            String fen = "4k3/8/8/8/8/8/8/R3K3 w - - 0 1";
            String body = json.writeValueAsString(Map.of("fen", fen));
            mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fen", startsWith("4k3")));
        }

        @Test
        @DisplayName("creates a human-vs-human game (aiColor=NONE)")
        void createHumanVsHuman() throws Exception {
            String body = json.writeValueAsString(Map.of("aiColor", "NONE"));
            mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").isNotEmpty());
        }

        @Test
        @DisplayName("returns 400 on invalid FEN")
        void createInvalidFen() throws Exception {
            String body = json.writeValueAsString(Map.of("fen", "not-a-fen"));
            mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 on invalid aiColor")
        void createInvalidAiColor() throws Exception {
            String body = json.writeValueAsString(Map.of("aiColor", "PURPLE"));
            mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }
    }

    // ================================================================
    // GET /api/games/{id}
    // ================================================================

    @Nested
    @DisplayName("GET /api/games/{id} — get state")
    class GetGame {

        @Test
        @DisplayName("returns full game state for a valid ID")
        void getExistingGame() throws Exception {
            String id = createGameDefault();
            mvc.perform(get("/api/games/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(id))
                .andExpect(jsonPath("$.activeColor").value("WHITE"))
                .andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.fen").isNotEmpty())
                .andExpect(jsonPath("$.legalMoves").isArray())
                .andExpect(jsonPath("$.moveHistory").isArray());
        }

        @Test
        @DisplayName("returns 404 for unknown game ID")
        void getUnknownGame() throws Exception {
            mvc.perform(get("/api/games/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Game Not Found"));
        }
    }

    // ================================================================
    // GET /api/games/{id}/legal-moves
    // ================================================================

    @Nested
    @DisplayName("GET /api/games/{id}/legal-moves — legal moves")
    class GetLegalMoves {

        @Test
        @DisplayName("returns 20 legal moves in starting position")
        void legalMovesStartPosition() throws Exception {
            String id = createGameDefault();
            mvc.perform(get("/api/games/" + id + "/legal-moves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalMoves", hasSize(20)))
                .andExpect(jsonPath("$.activeColor").value("WHITE"))
                .andExpect(jsonPath("$.legalMoves", hasItem("e2e4")));
        }

        @Test
        @DisplayName("returns 404 for unknown game")
        void legalMovesUnknownGame() throws Exception {
            mvc.perform(get("/api/games/ghost/legal-moves"))
                .andExpect(status().isNotFound());
        }
    }

    // ================================================================
    // POST /api/games/{id}/moves — human move
    // ================================================================

    @Nested
    @DisplayName("POST /api/games/{id}/moves — human move")
    class SubmitMove {

        @Test
        @DisplayName("valid move is accepted; board updates; turn switches")
        void validMove() throws Exception {
            String id   = createGame("BLACK"); // White=human, Black=AI
            String body = json.writeValueAsString(Map.of("move", "e2e4"));

            mvc.perform(post("/api/games/" + id + "/moves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeColor").value("BLACK"))
                .andExpect(jsonPath("$.lastMove").value("e2e4"))
                .andExpect(jsonPath("$.moveHistory", hasSize(1)));
        }

        @Test
        @DisplayName("illegal move returns 422 Unprocessable Entity")
        void illegalMove() throws Exception {
            String id   = createGame("BLACK");
            String body = json.writeValueAsString(Map.of("move", "e2e5")); // illegal

            mvc.perform(post("/api/games/" + id + "/moves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Illegal Move"));
        }

        @Test
        @DisplayName("move in wrong UCI format returns 400")
        void badFormat() throws Exception {
            String id   = createGame("BLACK");
            String body = json.writeValueAsString(Map.of("move", "z9z9")); // bad format

            mvc.perform(post("/api/games/" + id + "/moves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("move during AI's turn returns 409")
        void notYourTurn() throws Exception {
            // AI controls WHITE; we try to move as WHITE
            String id   = createGame("WHITE");
            String body = json.writeValueAsString(Map.of("move", "e2e4"));

            mvc.perform(post("/api/games/" + id + "/moves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Not Your Turn"));
        }

        @Test
        @DisplayName("move sequence: two white moves update history correctly")
        void twoMovesUpdateHistory() throws Exception {
            // Human vs Human
            String id    = createGame("NONE");
            String move1 = json.writeValueAsString(Map.of("move", "e2e4"));
            String move2 = json.writeValueAsString(Map.of("move", "e7e5"));

            mvc.perform(post("/api/games/" + id + "/moves")
                    .contentType(MediaType.APPLICATION_JSON).content(move1))
                .andExpect(status().isOk());

            mvc.perform(post("/api/games/" + id + "/moves")
                    .contentType(MediaType.APPLICATION_JSON).content(move2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moveHistory", hasSize(2)))
                .andExpect(jsonPath("$.moveHistory[0]").value("e2e4"))
                .andExpect(jsonPath("$.moveHistory[1]").value("e7e5"));
        }

        @Test
        @DisplayName("returns 404 for unknown game ID")
        void unknownGame() throws Exception {
            mvc.perform(post("/api/games/ghost/moves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("move", "e2e4"))))
                .andExpect(status().isNotFound());
        }
    }

    // ================================================================
    // POST /api/games/{id}/ai-move — AI move
    // ================================================================

    @Nested
    @DisplayName("POST /api/games/{id}/ai-move — AI move")
    class AiMove {

        @Test
        @DisplayName("AI plays a move after human's first move")
        void aiPlaysAfterHumanMove() throws Exception {
            // Human=WHITE plays e2e4, then AI=BLACK responds
            String id = createGame("BLACK");

            // Human moves first
            mvc.perform(post("/api/games/" + id + "/moves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("move", "e2e4"))))
                .andExpect(status().isOk());

            // AI responds
            mvc.perform(post("/api/games/" + id + "/ai-move"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeColor").value("WHITE"))
                .andExpect(jsonPath("$.moveHistory", hasSize(2)));
        }

        @Test
        @DisplayName("calling ai-move during human's turn returns 409")
        void aiMoveOnHumanTurn() throws Exception {
            String id = createGame("BLACK"); // White=human's turn first
            mvc.perform(post("/api/games/" + id + "/ai-move"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Not Your Turn"));
        }

        @Test
        @DisplayName("AI move on non-AI game (NONE) returns 409")
        void aiMoveOnHumanVsHumanGame() throws Exception {
            String id = createGame("NONE");
            mvc.perform(post("/api/games/" + id + "/ai-move"))
                .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("returns 404 for unknown game")
        void unknownGame() throws Exception {
            mvc.perform(post("/api/games/ghost/ai-move"))
                .andExpect(status().isNotFound());
        }
    }

    // ================================================================
    // DELETE /api/games/{id}
    // ================================================================

    @Nested
    @DisplayName("DELETE /api/games/{id} — delete game")
    class DeleteGame {

        @Test
        @DisplayName("deletes an existing game and returns 204")
        void deleteExisting() throws Exception {
            String id = createGameDefault();
            mvc.perform(delete("/api/games/" + id))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("deleted game is no longer retrievable")
        void deletedGameIsGone() throws Exception {
            String id = createGameDefault();
            mvc.perform(delete("/api/games/" + id));
            mvc.perform(get("/api/games/" + id))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deleting non-existent game returns 404")
        void deleteUnknown() throws Exception {
            mvc.perform(delete("/api/games/does-not-exist"))
                .andExpect(status().isNotFound());
        }
    }

    // ================================================================
    // Error response format (RFC-7807 ProblemDetail)
    // ================================================================

    @Nested
    @DisplayName("Error response format")
    class ErrorFormat {

        @Test
        @DisplayName("404 response has RFC-7807 ProblemDetail fields")
        void notFoundHasProblemDetail() throws Exception {
            mvc.perform(get("/api/games/no-such-game"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(
                    "https://chess-engine/errors/game-not-found"))
                .andExpect(jsonPath("$.title").value("Game Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isString());
        }

        @Test
        @DisplayName("422 illegal move has correct ProblemDetail type")
        void illegalMoveHasProblemDetail() throws Exception {
            String id = createGame("BLACK");
            mvc.perform(post("/api/games/" + id + "/moves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("move", "e2e6"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(
                    "https://chess-engine/errors/illegal-move"));
        }
    }
}

