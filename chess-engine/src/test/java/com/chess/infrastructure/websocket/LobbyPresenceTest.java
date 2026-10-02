package com.chess.infrastructure.websocket;

import com.chess.application.MatchmakingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Lobby sockets — presence and the matchmaking queue")
class LobbyPresenceTest {

    private final UUID userId = UUID.randomUUID();

    private LobbySessionManager lobby;
    private MatchmakingService matchmaking;
    private TaskScheduler scheduler;
    private LobbyWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        lobby       = new LobbySessionManager(new ObjectMapper());
        matchmaking = mock(MatchmakingService.class);
        scheduler   = mock(TaskScheduler.class);
        handler     = new LobbyWebSocketHandler(lobby, matchmaking, scheduler);
    }

    private WebSocketSession socket() {
        var s = mock(WebSocketSession.class);
        when(s.getAttributes()).thenReturn(Map.of("userId", userId.toString()));
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    /** Runs the clean-up the handler scheduled when a socket closed. */
    private void runScheduledCleanup() {
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), any(Instant.class));
        task.getValue().run();
    }

    @Test
    @DisplayName("a closing old tab doesn't knock out the newer socket")
    void oldSocketDoesNotRemoveTheNewOne() {
        var oldTab = socket();
        var newTab = socket();
        handler.afterConnectionEstablished(oldTab);
        handler.afterConnectionEstablished(newTab);

        handler.afterConnectionClosed(oldTab, CloseStatus.NORMAL);

        assertTrue(lobby.isConnected(userId.toString()));
    }

    @Test
    @DisplayName("a player whose socket is gone is taken out of the queue after a grace period")
    void leavesTheQueueAfterTheGracePeriod() {
        var socket = socket();
        handler.afterConnectionEstablished(socket);

        handler.afterConnectionClosed(socket, CloseStatus.GOING_AWAY);

        assertFalse(lobby.isConnected(userId.toString()));
        verify(matchmaking, never()).leaveQueue(any());   // not yet: it may only be a network blip
        runScheduledCleanup();
        verify(matchmaking).leaveQueue(userId);
    }

    @Test
    @DisplayName("a player who reconnects in time keeps their place")
    void reconnectingKeepsThePlace() {
        var first = socket();
        handler.afterConnectionEstablished(first);
        handler.afterConnectionClosed(first, CloseStatus.GOING_AWAY);

        handler.afterConnectionEstablished(socket());   // back within the grace period

        runScheduledCleanup();
        verify(matchmaking, never()).leaveQueue(any());
    }
}
