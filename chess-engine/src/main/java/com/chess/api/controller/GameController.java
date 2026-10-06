package com.chess.api.controller;

import com.chess.api.dto.*;
import com.chess.application.*;
import com.chess.domain.board.FenParser;
import com.chess.engine.eval.Evaluator;
import com.chess.engine.search.AlphaBetaSearch;
import com.chess.infrastructure.api.dto.GameStateResponse;
import com.chess.infrastructure.websocket.WebSocketSessionManager;
import com.chess.persistence.entity.DatabaseEnums.GameMode;
import com.chess.persistence.entity.DatabaseEnums.GameStatus;
import com.chess.persistence.entity.DatabaseEnums.PlayerSide;
import com.chess.persistence.entity.GameEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/games")
@Tag(name = "Games", description = "Chess game lifecycle — create, move, resign, chat")
public class GameController {

    private final GameApplicationService engineService;
    private final GamePersistenceService persistService;
    private final UserService userService;
    private final WebSocketSessionManager wsManager;
    private final GameAccess gameAccess;
    private final AlphaBetaSearch search = new AlphaBetaSearch();
    private final Evaluator evaluator = new Evaluator();

    public GameController(GameApplicationService engineService,
            GamePersistenceService persistService,
            UserService userService,
            WebSocketSessionManager wsManager,
            GameAccess gameAccess) {
        this.engineService  = engineService;
        this.persistService = persistService;
        this.userService    = userService;
        this.wsManager      = wsManager;
        this.gameAccess     = gameAccess;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new game")
    public GameDto.Game createGame(
            @Valid @RequestBody GameDto.CreateGameRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        String username = userDetails.getUsername();
        // "random" and a missing AI level are settled here, once, so the engine and the
        // database agree on them
        var request = req.withDefaultsResolved(() -> ThreadLocalRandom.current().nextBoolean());
        // 1. Start in the engine (in-memory, fast)
        var engineResp = engineService.createGame(toEngineRequest(request));
        // 2. Persist to DB
        persistService.persistNewGame(engineResp.gameId(), request, userId, username);
        // 3. Return full game DTO (engine state + DB metadata)
        return persistService.toFullGameDto(engineResp.gameId(), engineResp);
    }

    @GetMapping("/{gameId}")
    @Operation(summary = "Get current game state")
    public GameDto.Game getGame(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        requirePlayer(gameId, userDetails);
        var r = engineService.getGame(gameId);
        return persistService.toFullGameDto(gameId, r);
    }

    @GetMapping("/{gameId}/legal-moves")
    @Operation(summary = "List legal moves for the active player")
    public GameDto.LegalMovesResponse getLegalMoves(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        requirePlayer(gameId, userDetails);
        var resp = engineService.getLegalMoves(gameId);
        return new GameDto.LegalMovesResponse(resp.gameId(), resp.activeColor(), resp.legalMoves());
    }

    @PostMapping("/{gameId}/moves")
    @Operation(summary = "Submit a human move (UCI format)")
    public GameDto.Game submitMove(
            @PathVariable String gameId,
            @Valid @RequestBody GameDto.MoveRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID callerId = callerId(userDetails);
        GameEntity dbGame = gameAccess.requirePlayer(gameId, callerId);
        // Online: each player only moves their own pieces
        if (dbGame.getMode() == GameMode.online) {
            UUID expectedId = dbGame.getCurrentTurn() == PlayerSide.white
                ? dbGame.getWhiteUserId() : dbGame.getBlackUserId();
            if (!callerId.equals(expectedId))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "It is not your turn");
        }
        var r = engineService.submitMove(gameId, req.move(), GameAccess.seatOf(dbGame, callerId));
        var game = persistService.toFullGameDto(gameId, r);
        wsManager.broadcast(gameId, game.result() != null ? "GAME_OVER" : "MOVE_MADE", game);
        return game;
    }

    @PostMapping("/{gameId}/ai-move")
    @Operation(summary = "Let the AI play its move")
    public GameDto.Game playAiMove(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        requirePlayer(gameId, userDetails);
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
        // The caller resigns, whoever is to move (local games: the side to move)
        UUID callerId = callerId(userDetails);
        GameEntity dbGame = gameAccess.requirePlayer(gameId, callerId);
        var r = engineService.resign(gameId, GameAccess.seatOf(dbGame, callerId));
        var game = persistService.toFullGameDto(gameId, r);
        wsManager.broadcast(gameId, "GAME_OVER", game);
        return game;
    }

    @PostMapping("/{gameId}/draw-offer")
    @Operation(summary = "Offer a draw (accepts one the opponent has already offered)")
    public GameDto.Game offerDraw(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID callerId = callerId(userDetails);
        GameEntity dbGame = gameAccess.requirePlayer(gameId, callerId);
        var r = engineService.offerDraw(gameId, GameAccess.seatOf(dbGame, callerId));
        return broadcastDrawOutcome(gameId, r, "DRAW_OFFERED");
    }

    @PostMapping("/{gameId}/draw-offer/accept")
    @Operation(summary = "Accept the draw the opponent offered")
    public GameDto.Game acceptDraw(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID callerId = callerId(userDetails);
        GameEntity dbGame = gameAccess.requirePlayer(gameId, callerId);
        var r = engineService.acceptDraw(gameId, GameAccess.seatOf(dbGame, callerId));
        return broadcastDrawOutcome(gameId, r, "GAME_OVER");
    }

    @PostMapping("/{gameId}/draw-offer/decline")
    @Operation(summary = "Decline the draw the opponent offered")
    public GameDto.Game declineDraw(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID callerId = callerId(userDetails);
        GameEntity dbGame = gameAccess.requirePlayer(gameId, callerId);
        var r = engineService.declineDraw(gameId, GameAccess.seatOf(dbGame, callerId));
        return broadcastDrawOutcome(gameId, r, "DRAW_DECLINED");
    }

    @DeleteMapping("/{gameId}/moves/last")
    @Operation(summary = "Undo the last move, or the last two (a move against the AI and its reply)")
    public GameDto.Game undoMove(
            @PathVariable String gameId,
            @RequestParam(defaultValue = "1") int plies,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (requirePlayer(gameId, userDetails).getMode() == GameMode.online)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Undo is not available in online games");
        var r = engineService.undoLastMove(gameId, plies);
        return persistService.toFullGameDto(gameId, r);
    }

    @PostMapping("/{gameId}/save")
    @Operation(summary = "Save the current game state")
    public GameDto.SavedGame saveGame(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = callerId(userDetails);
        gameAccess.requirePlayer(gameId, userId);
        return persistService.saveCurrentGame(gameId, userId);
    }

    @GetMapping("/{gameId}/evaluation")
    @Operation(summary = "Evaluate the current position using the chess engine")
    public EvaluationDto.PositionEvaluation evaluate(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        GameEntity dbGame = requirePlayer(gameId, userDetails);
        // An engine during a live online game would be cheating; afterwards it is analysis
        if (dbGame.getMode() == GameMode.online && dbGame.getStatus() == GameStatus.active)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Engine evaluation is not available during an online game");
        String fen = engineService.getGame(gameId).fen();
        var board = FenParser.parse(fen);
        int score = evaluator.evaluate(board, board.activeColor());
        // the search keeps its table between calls, so only one request may use it at a time
        var best = bestMove(board);
        return new EvaluationDto.PositionEvaluation(
                score, 4,
                best.map(Object::toString).orElse(null),
                null);
    }

    @DeleteMapping("/{gameId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Abandon / delete a game session")
    public void deleteGame(
            @PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {
        // A hard delete: an online game is also part of the opponent's history
        if (requirePlayer(gameId, userDetails).getMode() == GameMode.online)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Online games can't be deleted");
        engineService.deleteGame(gameId);
    }

    private synchronized java.util.Optional<com.chess.domain.model.Move> bestMove(
            com.chess.domain.board.Board board) {
        return search.findBestMove(board, 4);
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



    /** Tells the players: GAME_OVER if the draw was agreed, else {@code eventIfOpen}. */
    private GameDto.Game broadcastDrawOutcome(String gameId, GameStateResponse r, String eventIfOpen) {
        var game = persistService.toFullGameDto(gameId, r);
        wsManager.broadcast(gameId, game.result() != null ? "GAME_OVER" : eventIfOpen, game);
        return game;
    }

    // ── Access helpers ────────────────────────────────────────────────────────

    private UUID callerId(UserDetails userDetails) {
        return userService.getUserIdByUsername(userDetails.getUsername());
    }

    /** The stored game if the caller plays in it: 404 for unknown ids, 403 for anyone else. */
    private GameEntity requirePlayer(String gameId, UserDetails userDetails) {
        return gameAccess.requirePlayer(gameId, callerId(userDetails));
    }
}
