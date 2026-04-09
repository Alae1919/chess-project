package com.chess.infrastructure.api;

import com.chess.application.GameApplicationService;
import com.chess.infrastructure.api.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the chess game API.
 *
 * Design principles applied here:
 *   • No domain objects in method signatures or return types (only DTOs).
 *   • No business logic — all decisions are in GameApplicationService.
 *   • HTTP status codes are explicit (@ResponseStatus or ResponseEntity).
 *   • @Valid on request bodies triggers Jakarta Bean Validation.
 */
@RestController
@RequestMapping("/api/games")
@Tag(name = "Games", description = "Chess game lifecycle management")
public class GameController {

    private final GameApplicationService service;

    public GameController(GameApplicationService service) {
        this.service = service;
    }

    // ----------------------------------------------------------------
    // POST /api/games — create
    // ----------------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new game",
               description = "Start a game from the starting position or a custom FEN. "
                           + "Specify which color the AI controls (default: BLACK).")
    @ApiResponse(responseCode = "201", description = "Game created")
    @ApiResponse(responseCode = "400", description = "Invalid FEN or parameters")
    public GameStateResponse createGame(
            @Valid @RequestBody(required = false) CreateGameRequest request) {
        if (request == null) request = new CreateGameRequest(null, null, null);
        return service.createGame(request);
    }

    // ----------------------------------------------------------------
    // GET /api/games/{id} — state
    // ----------------------------------------------------------------

    @GetMapping("/{gameId}")
    @Operation(summary = "Get full game state",
               description = "Returns board FEN, active color, game status, "
                           + "move history, and legal moves.")
    @ApiResponse(responseCode = "200", description = "Game state")
    @ApiResponse(responseCode = "404", description = "Game not found")
    public GameStateResponse getGame(@PathVariable String gameId) {
        return service.getGame(gameId);
    }

    // ----------------------------------------------------------------
    // GET /api/games/{id}/legal-moves — legal moves only
    // ----------------------------------------------------------------

    @GetMapping("/{gameId}/legal-moves")
    @Operation(summary = "Get legal moves",
               description = "Returns all legal moves for the active player in UCI format. "
                           + "Empty list when the game is over.")
    @ApiResponse(responseCode = "200", description = "Legal moves")
    @ApiResponse(responseCode = "404", description = "Game not found")
    public LegalMovesResponse getLegalMoves(@PathVariable String gameId) {
        return service.getLegalMoves(gameId);
    }

    // ----------------------------------------------------------------
    // POST /api/games/{id}/moves — human move
    // ----------------------------------------------------------------

    @PostMapping("/{gameId}/moves")
    @Operation(summary = "Submit a human move",
               description = "Apply a move in UCI format (e.g. e2e4). "
                           + "Returns 422 if the move is illegal, "
                           + "409 if the game is over or it is the AI's turn.")
    @ApiResponse(responseCode = "200", description = "Move applied; new state returned")
    @ApiResponse(responseCode = "404", description = "Game not found")
    @ApiResponse(responseCode = "409", description = "Game over or not your turn")
    @ApiResponse(responseCode = "422", description = "Illegal move")
    public GameStateResponse submitMove(
            @PathVariable String gameId,
            @Valid @RequestBody MoveRequest request) {
        return service.submitMove(gameId, request.move());
    }

    // ----------------------------------------------------------------
    // POST /api/games/{id}/ai-move — AI move
    // ----------------------------------------------------------------

    @PostMapping("/{gameId}/ai-move")
    @Operation(summary = "Ask the AI to play",
               description = "The AI chooses and applies its move. "
                           + "Returns 409 if it is not the AI's turn or game is over.")
    @ApiResponse(responseCode = "200", description = "AI move applied; new state returned")
    @ApiResponse(responseCode = "404", description = "Game not found")
    @ApiResponse(responseCode = "409", description = "Not the AI's turn or game over")
    public GameStateResponse playAiMove(@PathVariable String gameId) {
        return service.playAiMove(gameId);
    }

    // ----------------------------------------------------------------
    // DELETE /api/games/{id} — abandon
    // ----------------------------------------------------------------

    @DeleteMapping("/{gameId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Abandon / delete a game",
               description = "Removes the game from the server. "
                           + "Useful for cleaning up after a game ends or the client disconnects.")
    @ApiResponse(responseCode = "204", description = "Game deleted")
    @ApiResponse(responseCode = "404", description = "Game not found")
    public void deleteGame(@PathVariable String gameId) {
        service.deleteGame(gameId);
    }
}
