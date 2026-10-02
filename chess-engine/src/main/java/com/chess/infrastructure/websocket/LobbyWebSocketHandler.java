package com.chess.infrastructure.websocket;

import com.chess.application.MatchmakingService;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handles the /ws/lobby endpoint lifecycle.
 * All communication is server → client only (no inbound message processing).
 * Actual message delivery is done via LobbySessionManager.
 */
@Component
public class LobbyWebSocketHandler extends TextWebSocketHandler {

    /** A brief network drop shouldn't cost a player their place in the queue. */
    private static final Duration QUEUE_GRACE = Duration.ofSeconds(10);

    private final LobbySessionManager lobbySessionManager;
    private final MatchmakingService  matchmaking;
    private final TaskScheduler       scheduler;

    public LobbyWebSocketHandler(LobbySessionManager lobbySessionManager,
                                 MatchmakingService matchmaking, TaskScheduler scheduler) {
        this.lobbySessionManager = lobbySessionManager;
        this.matchmaking         = matchmaking;
        this.scheduler           = scheduler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            lobbySessionManager.register(userId, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        left(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        left(session);
    }

    /**
     * Nobody is listening for a match any more once the socket is gone, so a queued
     * player is taken out of the queue, unless they are back within the grace period.
     */
    private void left(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) return;
        lobbySessionManager.unregister(userId, session);
        scheduler.schedule(() -> {
            if (!lobbySessionManager.isConnected(userId)) matchmaking.leaveQueue(UUID.fromString(userId));
        }, Instant.now().plus(QUEUE_GRACE));
    }
}
