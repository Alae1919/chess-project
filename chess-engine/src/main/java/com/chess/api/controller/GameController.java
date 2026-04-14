package com.chess.api.controller;

import com.chess.api.dto.*;
import com.chess.application.*;
import com.chess.domain.board.FenParser;
import com.chess.engine.eval.Evaluator;
import com.chess.engine.search.AlphaBetaSearch;
import com.chess.infrastructure.websocket.WebSocketSessionManager;
import com.chess.persistence.entity.DatabaseEnums.GameMode;
import com.chess.persistence.entity.DatabaseEnums.PlayerSide;
import com.chess.persistence.repository.GameRepository;
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
    private final UserService userService;
    private final WebSocketSessionManager wsManager;
    private final GameRepository gameRepository;
    private final AlphaBetaSearch search = new AlphaBetaSearch();
    private final Evaluator evaluator = new Evaluator();

    public GameController(GameApplicationService engineService,
            GamePersistenceService persistService,
            UserService userService,
            WebSocketSessionManager wsManager,
            GameRepository gameRepository) {
        this.engineService  = engineService;
        this.persistService = persistService;
        this.userService    = userService;
        this.wsManager      = wsManager;
        this.gameRepository = gameRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new game")
    public GameDto.Game createGame(
            @Valid @RequestBody GameDto.CreateGameRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        String username = userDetails.getUsername();
        // 1. Start in the engine (in-memory, fast)
        var engineResp = engineService.createGame(toEngineRequest(req));
        // 2. Persist to DB
        persistService.persistNewGame(engineResp.gameId(), req, userId, username);
        // 3. Return full game DTO (engine state + DB metadata)
        return persistService.toFullGameDto(engineResp.gameId(), engineResp);
    }

    @GetMapping("/{gameId}")
    @Operation(summary = "Get current game state")
    public GameDto.Game getGame(@PathVariable String gameId) {
        var r = engineService.getGame(gameId);
        return persistService.toFullGameDto(gameId, r);
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
            @Valid @RequestBody GameDto.MoveRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        // For online games, verify the caller is the player whose turn it is
        if (userDetails != null) {
            var dbGame = gameRepository.findById(UUID.fromString(gameId));
            dbGame.ifPresent(g -> {
                if (g.getMode() == GameMode.online) {
                    UUID callerId = userService.getUserIdByUsername(userDetails.getUsername());
                    PlayerSide turn = g.getCurrentTurn();
                    UUID expectedId = turn == PlayerSide.white ? g.getWhiteUserId() : g.getBlackUserId();
                    if (!callerId.equals(expectedId)) {
                        throw new org.springframework.web.server.ResponseStatusException(
                            HttpStatus.FORBIDDEN, "It is not your turn");
                    }
                }
            });
        }
        var r = engineService.submitMove(gameId, req.move());
        var game = persistService.toFullGameDto(gameId, r);
        wsManager.broadcast(gameId, game.result() != null ? "GAME_OVER" : "MOVE_MADE", game);
        return game;
    }

    @PostMapping("/{gameId}/ai-move")
    @Operation(summary = "Let the AI play its move")
    public GameDto.Game playAiMove(@PathVariable String gameId) {
        var r = engineService.playAiMove(gameId);
        var game = persistService.toFullGameDto(gameId, r);
        wsManager.broadcast(gameId, game.result() != null ? "GAME_OVER" : "MOVE_MADE", game);
        return game;
    }

    @PostMapping("/{gameId}/resign")
    @Operation(summary = "Resign the current game")
    public GameDto.Game resign(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        var r = engineService.resign(gameId, userDetails.getUsername());
        var game = persistService.toFullGameDto(gameId, r);
        wsManager.broadcast(gameId, "GAME_OVER", game);
        return game;
    }

    @PostMapping("/{gameId}/draw-offer")
    @Operation(summary = "Offer or accept a draw")
    public GameDto.Game offerDraw(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        var r = engineService.offerDraw(gameId, userDetails.getUsername());
        var game = persistService.toFullGameDto(gameId, r);
        wsManager.broadcast(gameId, "GAME_OVER", game);
        return game;
    }

    @DeleteMapping("/{gameId}/moves/last")
    @Operation(summary = "Undo the last move")
    public GameDto.Game undoMove(@PathVariable String gameId) {
        var r = engineService.undoLastMove(gameId);
        return persistService.toFullGameDto(gameId, r);
    }

    @PostMapping("/{gameId}/save")
    @Operation(summary = "Save the current game state")
    public GameDto.SavedGame saveGame(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        return persistService.saveCurrentGame(gameId, userId);
    }

    @GetMapping("/{gameId}/evaluation")
    @Operation(summary = "Evaluate the current position using the chess engine")
    public EvaluationDto.PositionEvaluation evaluate(@PathVariable String gameId) {
        String fen = engineService.getGame(gameId).fen();
        var board = FenParser.parse(fen);
        int score = evaluator.evaluate(board, board.activeColor());
        var best = search.findBestMove(board, 4);
        return new EvaluationDto.PositionEvaluation(
                score, 4,
                best.map(Object::toString).orElse(null),
                null);
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


}
