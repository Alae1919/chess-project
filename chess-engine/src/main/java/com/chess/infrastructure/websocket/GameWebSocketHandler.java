package com.chess.infrastructure.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;

    public GameWebSocketHandler(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String gameId = extractGameId(session);
        if (gameId != null) {
            sessionManager.register(gameId, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String gameId = extractGameId(session);
        if (gameId != null) {
            sessionManager.unregister(gameId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String gameId = extractGameId(session);
        if (gameId != null) {
            sessionManager.unregister(gameId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Moves are submitted via REST; no client-to-server WS frames expected
    }

    /** Extracts the gameId path segment from /ws/game/{gameId} */
    private String extractGameId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : null;
        if (path == null) return null;
        // path looks like /ws/game/42a7ec90-d66d-48ef-913e-79035223489f
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < path.length() - 1
                ? path.substring(lastSlash + 1)
                : null;
    }
}
