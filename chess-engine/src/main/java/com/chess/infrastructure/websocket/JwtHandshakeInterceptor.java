package com.chess.infrastructure.websocket;

import com.chess.application.GameAccess;
import com.chess.security.JwtService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * Authenticates /ws/game/{gameId} and admits only that game's players, since
 * the socket carries its moves and chat.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final GameAccess gameAccess;

    public JwtHandshakeInterceptor(JwtService jwtService, GameAccess gameAccess) {
        this.jwtService = jwtService;
        this.gameAccess = gameAccess;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        if (query == null) return false;

        String token = null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                token = param.substring("token=".length());
                break;
            }
        }
        if (token == null || token.isBlank()) return false;

        try {
            var claims = jwtService.validateAndParse(token);
            if ("refresh".equals(claims.get("type", String.class))) return false;

            UUID userId   = UUID.fromString(claims.getSubject());
            String path   = request.getURI().getPath();
            String gameId = path.substring(path.lastIndexOf('/') + 1);
            gameAccess.requirePlayer(gameId, userId);

            attributes.put("userId", userId.toString());
            return true;
        } catch (Exception e) {
            // bad token, unknown game, or not one of its players
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // nothing to do
    }
}
