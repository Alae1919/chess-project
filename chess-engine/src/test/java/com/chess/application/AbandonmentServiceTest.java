package com.chess.application;

import com.chess.domain.model.Color;
import com.chess.infrastructure.api.dto.GameStateResponse;
import com.chess.infrastructure.websocket.WebSocketSessionManager;
import com.chess.persistence.entity.DatabaseEnums.GameMode;
import com.chess.persistence.entity.DatabaseEnums.GameStatus;
import com.chess.persistence.entity.GameEntity;
import com.chess.persistence.repository.GameRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AbandonmentService — leaving an online game")
class AbandonmentServiceTest {

    private static final long TIMEOUT_MS = 200;

    private final String gameId = UUID.randomUUID().toString();
    private final UUID whiteId = UUID.randomUUID();
    private final UUID blackId = UUID.randomUUID();

    private WebSocketSessionManager sockets;
    private GameRepository games;
    private GameApplicationService engine;
    private GamePersistenceService persistence;
    private ThreadPoolTaskScheduler scheduler;
    private AbandonmentService service;

    @BeforeEach
    void setUp() {
        sockets     = mock(WebSocketSessionManager.class);
        games       = mock(GameRepository.class);
        engine      = mock(GameApplicationService.class);
        persistence = mock(GamePersistenceService.class);
        scheduler   = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        service = new AbandonmentService(sockets, games, engine, persistence, scheduler,
            Duration.ofMillis(TIMEOUT_MS));

        when(games.findById(UUID.fromString(gameId))).thenReturn(Optional.of(game(GameMode.online)));
        when(sockets.hasAnyConnection(gameId)).thenReturn(true);
        when(engine.abandon(eq(gameId), any())).thenReturn(Optional.of(new GameStateResponse(
            gameId, "fen", "WHITE", "WHITE_ABANDONED", null, List.of(), List.of(), List.of())));
    }

    @AfterEach
    void tearDown() { scheduler.shutdown(); }

    private GameEntity game(GameMode mode) {
        var g = new GameEntity();
        g.setMode(mode);
        g.setStatus(GameStatus.active);
        g.setWhiteUserId(whiteId);
        g.setBlackUserId(blackId);
        return g;
    }

    private void waitPastTheTimeout() throws InterruptedException { Thread.sleep(TIMEOUT_MS * 3); }

    @Test
    @DisplayName("the opponent is told, and the player who stayed wins after the timeout")
    void leaverLosesAfterTheTimeout() {
        service.playerDisconnected(gameId, whiteId.toString());

        verify(sockets).broadcast(eq(gameId), eq("OPPONENT_DISCONNECTED"),
            eq(Map.of("color", "white", "timeoutMs", TIMEOUT_MS)));

        verify(engine, timeout(TIMEOUT_MS * 5)).abandon(gameId, Color.WHITE);
        verify(sockets, timeout(TIMEOUT_MS * 5)).broadcast(eq(gameId), eq("GAME_OVER"), any());
    }

    @Test
    @DisplayName("coming back in time cancels the countdown")
    void reconnectingCancelsIt() throws Exception {
        service.playerDisconnected(gameId, blackId.toString());
        service.playerConnected(gameId, blackId.toString(), mock(WebSocketSession.class));

        verify(sockets).broadcast(gameId, "OPPONENT_RECONNECTED", Map.of("color", "black"));
        waitPastTheTimeout();
        verify(engine, never()).abandon(any(), any());
    }

    @Test
    @DisplayName("a player with another tab still open has not left")
    void anotherTabKeepsThePlayerHere() throws Exception {
        when(sockets.isConnected(gameId, whiteId.toString())).thenReturn(true);

        service.playerDisconnected(gameId, whiteId.toString());

        waitPastTheTimeout();
        verify(sockets, never()).broadcast(eq(gameId), eq("OPPONENT_DISCONNECTED"), any());
        verify(engine, never()).abandon(any(), any());
    }

    @Test
    @DisplayName("only online games are watched")
    void aiAndLocalGamesAreIgnored() throws Exception {
        when(games.findById(UUID.fromString(gameId))).thenReturn(Optional.of(game(GameMode.ai)));

        service.playerDisconnected(gameId, whiteId.toString());

        waitPastTheTimeout();
        verify(sockets, never()).broadcast(any(), any(), any());
        verify(engine, never()).abandon(any(), any());
    }

    @Test
    @DisplayName("a stranger's socket does not start a countdown")
    void nonPlayersAreIgnored() throws Exception {
        service.playerDisconnected(gameId, UUID.randomUUID().toString());

        waitPastTheTimeout();
        verify(sockets, never()).broadcast(any(), any(), any());
    }

    @Test
    @DisplayName("if both players are gone from a game that was played, the one who left first loses")
    void bothGoneFirstLeaverLoses() throws Exception {
        when(sockets.hasAnyConnection(gameId)).thenReturn(false);
        when(engine.getMoveCount(gameId)).thenReturn(12);

        service.playerDisconnected(gameId, whiteId.toString());

        verify(engine, timeout(TIMEOUT_MS * 5)).abandon(gameId, Color.WHITE);
        verify(engine, never()).abort(any());
        verify(sockets, timeout(TIMEOUT_MS * 5)).broadcast(eq(gameId), eq("GAME_OVER"), any());
    }

    @Test
    @DisplayName("if both players are gone before the game really began, it is aborted, not scored")
    void bothGoneEarlyIsAborted() throws Exception {
        when(sockets.hasAnyConnection(gameId)).thenReturn(false);
        when(engine.getMoveCount(gameId)).thenReturn(1);
        when(engine.abort(gameId)).thenReturn(Optional.of(new GameStateResponse(
            gameId, "fen", "WHITE", "ABORTED", null, List.of(), List.of(), List.of())));

        service.playerDisconnected(gameId, whiteId.toString());

        verify(engine, timeout(TIMEOUT_MS * 5)).abort(gameId);
        verify(engine, never()).abandon(any(), any());
        verify(sockets, timeout(TIMEOUT_MS * 5)).broadcast(eq(gameId), eq("GAME_OVER"), any());
    }

    @Test
    @DisplayName("the second player to leave does not start a second ending")
    void secondLeaverIsNotScoredAgain() throws Exception {
        when(sockets.hasAnyConnection(gameId)).thenReturn(false);
        when(engine.getMoveCount(gameId)).thenReturn(12);
        when(engine.abandon(eq(gameId), eq(Color.WHITE))).thenReturn(Optional.of(new GameStateResponse(
            gameId, "fen", "WHITE", "WHITE_ABANDONED", null, List.of(), List.of(), List.of())));
        when(engine.abandon(eq(gameId), eq(Color.BLACK))).thenReturn(Optional.empty()); // already over

        service.playerDisconnected(gameId, whiteId.toString());
        service.playerDisconnected(gameId, blackId.toString());

        verify(engine, timeout(TIMEOUT_MS * 5)).abandon(gameId, Color.WHITE);
        waitPastTheTimeout();
        verify(sockets, times(1)).broadcast(eq(gameId), eq("GAME_OVER"), any());
    }

    @Test
    @DisplayName("someone connecting while the opponent is away sees the countdown")
    void newcomerSeesTheCountdown() {
        var newcomer = mock(WebSocketSession.class);
        service.playerDisconnected(gameId, whiteId.toString());

        service.playerConnected(gameId, blackId.toString(), newcomer);

        verify(sockets).send(eq(newcomer), eq("OPPONENT_DISCONNECTED"),
            ArgumentMatchers.<Object>eq(Map.of("color", "white", "timeoutMs", TIMEOUT_MS)));
    }
}
