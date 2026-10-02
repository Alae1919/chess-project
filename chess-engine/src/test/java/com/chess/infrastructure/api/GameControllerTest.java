package com.chess.infrastructure.api;

import com.chess.ChessApplication;
import com.chess.application.GameApplicationService;
import com.chess.application.GamePersistenceService;
import com.chess.application.UserService;
import com.chess.infrastructure.api.dto.CreateGameRequest;
import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import com.chess.persistence.entity.UserEntity;
import com.chess.persistence.entity.UserPreferencesEntity;
import com.chess.persistence.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
@WithMockUser(username = "user")
@DisplayName("GameController Integration Tests")
class GameControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean  UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired GameApplicationService engineService;
    @Autowired GamePersistenceService persistService;

    private UUID playerId;
    private UUID opponentId;

    @BeforeEach
    void stubUserService() {
        // "user" (the @WithMockUser principal) plays every game it creates;
        // "intruder" plays none of them
        playerId   = ensureUser("gc_test_player");
        opponentId = ensureUser("gc_test_opponent");
        UUID intruderId = ensureUser("gc_test_intruder");
        Mockito.when(userService.getUserIdByUsername("user")).thenReturn(playerId);
        Mockito.when(userService.getUserIdByUsername("intruder")).thenReturn(intruderId);
    }

    /** Games reference real user rows, so the test players must exist (kept across runs). */
    private UUID ensureUser(String username) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            var u = new UserEntity();
            u.setUsername(username);
            u.setEmail(username + "@example.com");
            u.setPasswordHash("unused");
            u.setPreferences(UserPreferencesEntity.defaultsFor(u));
            return userRepository.save(u);
        }).getId();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /** Builds a minimal valid POST /api/games request body. */
    private Map<String, Object> gameBody(String aiColor) {
        Map<String, Object> tc   = Map.of("type", "unlimited", "initialMs", 0L, "incrementMs", 0L);
        Map<String, Object> body = new LinkedHashMap<>();
        if ("NONE".equals(aiColor)) {
            body.put("mode", "local");
            body.put("playerColor", "white");
        } else {
            body.put("mode", "ai");
            // "BLACK" means AI plays black → human plays white
            body.put("playerColor", "WHITE".equals(aiColor) ? "black" : "white");
        }
        body.put("aiDifficulty", 4);
        body.put("timeControl", tc);
        return body;
    }

    /** An online game between "user" (white) and another player, as matchmaking creates it. */
    private String createOnlineGame() {
        return createOnlineGame(true);
    }

    private String createOnlineGame(boolean userIsWhite) {
        String id = engineService.createGame(new CreateGameRequest(null, "NONE", 1)).gameId();
        UUID white = userIsWhite ? playerId : opponentId;
        UUID black = userIsWhite ? opponentId : playerId;
        persistService.persistNewOnlineGame(id,
            white, "white_player", 1200,
            black, "black_player", 1200,
            TimeControlKind.unlimited, 0, 0);
        return id;
    }

    /** Creates a game and returns its ID. */
    private String createGame(String aiColor) throws Exception {
        MvcResult result = mvc.perform(post("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(gameBody(aiColor))))
            .andExpect(status().isCreated())
            .andReturn();
        return json.readTree(result.getResponse().getContentAsString())
            .get("id").asText();
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
                    .content(json.writeValueAsString(gameBody("BLACK"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.currentTurn").value("white"))
                .andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.legalMoves", hasSize(20)))
                .andExpect(jsonPath("$.moveHistory", hasSize(0)))
                .andExpect(jsonPath("$.lastMove").value(is(nullValue())));
        }

        @Test
        @DisplayName("creates a game with minimal body (all defaults)")
        void createNoBody() throws Exception {
            mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(gameBody("BLACK"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());
        }

        @Test
        @DisplayName("creates a game from a custom FEN")
        void createFromFen() throws Exception {
            String fen = "4k3/8/8/8/8/8/8/R3K3 w - - 0 1";
            Map<String, Object> body = new LinkedHashMap<>(gameBody("BLACK"));
            body.put("fen", fen);
            mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fen", startsWith("4k3")));
        }

        @Test
        @DisplayName("creates a human-vs-human game (aiColor=NONE)")
        void createHumanVsHuman() throws Exception {
            mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(gameBody("NONE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());
        }

        @Test
        @DisplayName("returns 400 on invalid FEN")
        void createInvalidFen() throws Exception {
            Map<String, Object> body = new LinkedHashMap<>(gameBody("BLACK"));
            body.put("fen", "not-a-fen");
            mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 on blank mode")
        void createInvalidAiColor() throws Exception {
            Map<String, Object> body = Map.of(
                "mode", "",
                "timeControl", Map.of("type", "unlimited", "initialMs", 0L, "incrementMs", 0L));
            mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
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
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.currentTurn").value("white"))
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
                .andExpect(jsonPath("$.currentTurn").value("black"))
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
        @DisplayName("move sequence: two moves update history correctly")
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

        @ParameterizedTest(name = "e7e8{0} promotes to {1}")
        @CsvSource({ "q, Q", "n, N" })
        void promotion(String letter, String fenPiece) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>(gameBody("NONE"));
            body.put("fen", "k7/4P3/8/8/8/8/8/4K3 w - - 0 1");
            String id = json.readTree(mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                // legal moves are listed in the same notation the endpoint accepts
                .andExpect(jsonPath("$.legalMoves", hasItems("e7e8q", "e7e8r", "e7e8b", "e7e8n")))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

            mvc.perform(post("/api/games/" + id + "/moves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("move", "e7e8" + letter))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fen", startsWith("k3" + fenPiece + "3/")))
                .andExpect(jsonPath("$.lastMove").value("e7e8" + letter));
        }

        @Test
        @DisplayName("a pawn reaching the last rank needs a promotion piece")
        void promotionWithoutPieceIsIllegal() throws Exception {
            Map<String, Object> body = new LinkedHashMap<>(gameBody("NONE"));
            body.put("fen", "k7/4P3/8/8/8/8/8/4K3 w - - 0 1");
            String id = json.readTree(mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

            mvc.perform(post("/api/games/" + id + "/moves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("move", "e7e8"))))
                .andExpect(status().isUnprocessableEntity());
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
                .andExpect(jsonPath("$.currentTurn").value("white"))
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
    // POST /api/games/{id}/resign
    // ================================================================

    @Nested
    @DisplayName("POST /api/games/{id}/resign — the caller resigns")
    class Resign {

        @Test
        @DisplayName("AI game: the human resigns even while the AI is to move")
        void humanResignsOnTheAisTurn() throws Exception {
            String id = createGame("BLACK"); // human = white
            mvc.perform(post("/api/games/" + id + "/moves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("move", "e2e4"))))
                .andExpect(status().isOk());

            mvc.perform(post("/api/games/" + id + "/resign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WHITE_RESIGNED"))
                .andExpect(jsonPath("$.result.winner").value("black"));
        }

        @Test
        @DisplayName("online game: black can resign while white is to move")
        void blackResignsOnWhitesTurn() throws Exception {
            String id = createOnlineGame(false); // user = black, white to move
            mvc.perform(post("/api/games/" + id + "/resign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLACK_RESIGNED"))
                .andExpect(jsonPath("$.result.winner").value("white"));
        }

        @Test
        @DisplayName("local game: the side to move resigns")
        void localGameResignsTheSideToMove() throws Exception {
            String id = createGame("NONE");
            mvc.perform(post("/api/games/" + id + "/resign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WHITE_RESIGNED"));
        }
    }

    // ================================================================
    // Access — only a game's players may act on it
    // ================================================================

    @Nested
    @DisplayName("Access — only a game's players may act on it")
    class PlayersOnly {

        @ParameterizedTest(name = "{0} /api/games/<id>{1} by another user returns 403")
        @CsvSource({
            "GET,    ''",
            "GET,    /legal-moves",
            "POST,   /moves",
            "POST,   /ai-move",
            "POST,   /resign",
            "POST,   /draw-offer",
            "DELETE, /moves/last",
            "POST,   /save",
            "GET,    /evaluation",
            "DELETE, ''",
            "GET,    /chat",
            "POST,   /chat",
        })
        void anotherUserIsForbidden(String method, String path) throws Exception {
            String id = createGame("NONE");
            // one body that satisfies both the move and the chat request validation
            mvc.perform(request(HttpMethod.valueOf(method), "/api/games/" + id + path)
                    .with(user("intruder"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"move\":\"e2e4\",\"content\":\"hi\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Not A Player"));
        }

        @Test
        @DisplayName("the intruder's attempt leaves the game untouched")
        void forbiddenMoveIsNotPlayed() throws Exception {
            String id = createGame("NONE");
            mvc.perform(post("/api/games/" + id + "/moves")
                    .with(user("intruder"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("move", "e2e4"))))
                .andExpect(status().isForbidden());

            mvc.perform(get("/api/games/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moveHistory", hasSize(0)));
        }

        @Test
        @DisplayName("online games: no undo, no delete and no engine help while playing")
        void onlineGameRestrictions() throws Exception {
            String id = createOnlineGame();
            mvc.perform(post("/api/games/" + id + "/moves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("move", "e2e4"))))
                .andExpect(status().isOk());

            mvc.perform(delete("/api/games/" + id + "/moves/last"))
                .andExpect(status().isForbidden());
            mvc.perform(delete("/api/games/" + id))
                .andExpect(status().isForbidden());
            mvc.perform(get("/api/games/" + id + "/evaluation"))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("online games can't be created directly")
        void onlineModeIsRejectedOnCreate() throws Exception {
            Map<String, Object> body = new LinkedHashMap<>(gameBody("NONE"));
            body.put("mode", "online");
            mvc.perform(post("/api/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
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
