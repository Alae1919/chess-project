package com.chess.infrastructure.websocket;

import com.chess.application.AbandonmentService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final AbandonmentService      abandonment;

    public GameWebSocketHandler(WebSocketSessionManager sessionManager, AbandonmentService abandonment) {
        this.sessionManager = sessionManager;
        this.abandonment    = abandonment;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String gameId = extractGameId(session);
        if (gameId != null) {
            sessionManager.register(gameId, session);
            abandonment.playerConnected(gameId, userId(session), session);
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

    private void left(WebSocketSession session) {
        String gameId = extractGameId(session);
        if (gameId != null) {
            sessionManager.unregister(gameId, session);
            abandonment.playerDisconnected(gameId, userId(session));
        }
    }

    private static String userId(WebSocketSession session) {
        return (String) session.getAttributes().get("userId");
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
