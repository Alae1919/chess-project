package com.chess.infrastructure.websocket;

import com.chess.security.JwtService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Validates the JWT token for the /ws/lobby endpoint and extracts
 * both username and userId (UUID) from the token claims into session attributes.
 */
@Component
public class LobbyJwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public LobbyJwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
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
            String username = claims.get("username", String.class);
            String userId   = claims.getSubject(); // UUID string stored as JWT sub
            if (username == null || userId == null) return false;
            attributes.put("username", username);
            attributes.put("userId", userId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // nothing to do
    }
}
