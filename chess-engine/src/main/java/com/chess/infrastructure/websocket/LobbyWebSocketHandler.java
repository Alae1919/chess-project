package com.chess.infrastructure.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handles the /ws/lobby endpoint lifecycle.
 * All communication is server → client only (no inbound message processing).
 * Actual message delivery is done via LobbySessionManager.
 */
@Component
public class LobbyWebSocketHandler extends TextWebSocketHandler {

    private final LobbySessionManager lobbySessionManager;

    public LobbyWebSocketHandler(LobbySessionManager lobbySessionManager) {
        this.lobbySessionManager = lobbySessionManager;
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
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            lobbySessionManager.unregister(userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            lobbySessionManager.unregister(userId);
        }
    }
}
