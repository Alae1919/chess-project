package com.chess.api.controller;

import com.chess.api.dto.*;
import com.chess.application.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@Tag(name = "Games", description = "Chess game lifecycle — create, move, resign, chat")
public class GameController {

    private final GameApplicationService engineService;
    private final GamePersistenceService persistService;
    private final UserService            userService;

    public GameController(GameApplicationService engineService,
                          GamePersistenceService persistService,
                          UserService userService) {
        this.engineService  = engineService;
        this.persistService = persistService;
        this.userService    = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new game")
    public GameDto.Game createGame(
            @Valid @RequestBody GameDto.CreateGameRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId   = userService.getUserIdByUsername(userDetails.getUsername());
        String username = userDetails.getUsername();
        // 1. Start in the engine (in-memory, fast)
        var engineResp = engineService.createGame(toEngineRequest(req));
        // 2. Persist to DB
        persistService.persistNewGame(engineResp.gameId(), req, userId, username);
        // 3. Return full game DTO
        return toGameDto(engineResp);
    }

    @GetMapping("/{gameId}")
    @Operation(summary = "Get current game state")
    public GameDto.Game getGame(@PathVariable String gameId) {
        return toGameDto(engineService.getGame(gameId));
    }

    @GetMapping("/{gameId}/legal-moves")
    @Operation(summary = "List legal moves for the active player")
    public GameDto.LegalMovesResponse getLegalMoves(@PathVariable String gameId) {
        var resp = engineService.getLegalMoves(gameId);
        return new GameDto.LegalMovesResponse(resp.gameId(), resp.activeColor(), resp.legalMoves());
    }

    @PostMapping("/{gameId}/moves")
    @Operation(summary = "Submit a human move (UCI format)")
    public GameDto.Game submitMove(
            @PathVariable String gameId,
            @Valid @RequestBody GameDto.MoveRequest req) {
        var resp = engineService.submitMove(gameId, req.move());
        return toGameDto(resp);
    }

    @PostMapping("/{gameId}/ai-move")
    @Operation(summary = "Let the AI play its move")
    public GameDto.Game playAiMove(@PathVariable String gameId) {
        return toGameDto(engineService.playAiMove(gameId));
    }

    @PostMapping("/{gameId}/resign")
    @Operation(summary = "Resign the current game")
    public GameDto.Game resign(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return toGameDto(engineService.resign(gameId, userDetails.getUsername()));
    }

    @PostMapping("/{gameId}/draw-offer")
    @Operation(summary = "Offer or accept a draw")
    public GameDto.Game offerDraw(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return toGameDto(engineService.offerDraw(gameId, userDetails.getUsername()));
    }

    @DeleteMapping("/{gameId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Abandon / delete a game session")
    public void deleteGame(@PathVariable String gameId) {
        engineService.deleteGame(gameId);
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private com.chess.infrastructure.api.dto.CreateGameRequest toEngineRequest(
            GameDto.CreateGameRequest req) {
        String aiColor = "ai".equals(req.mode())
            ? ("black".equalsIgnoreCase(req.playerColor()) ? "WHITE" : "BLACK")
            : "NONE";
        return new com.chess.infrastructure.api.dto.CreateGameRequest(
            req.fen(), aiColor, req.aiDifficulty());
    }

    private GameDto.Game toGameDto(com.chess.infrastructure.api.dto.GameStateResponse r) {
        return new GameDto.Game(
            r.gameId(), null, r.status(),
            null, null,             // playerWhite / playerBlack — fetched from DB if needed
            null,                   // board — derived from FEN on the frontend
            null,                   // moves list — from /legal-moves or DB
            r.activeColor().toLowerCase(),
            null, null, null, 0, 0, // timeControl, ep, castling, clocks
            r.status().contains("CHECKMATE") || r.status().contains("STALEMATE")
                ? new GameDto.GameResult(null, r.status().toLowerCase()) : null,
            null,
            null, null,
            r.fen(),
            r.legalMoves()
        );
    }
}
