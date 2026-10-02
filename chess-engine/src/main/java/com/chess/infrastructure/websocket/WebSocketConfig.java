package com.chess.infrastructure.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler        gameWebSocketHandler;
    private final JwtHandshakeInterceptor     jwtHandshakeInterceptor;
    private final LobbyWebSocketHandler       lobbyWebSocketHandler;
    private final LobbyJwtHandshakeInterceptor lobbyJwtHandshakeInterceptor;
    private final String[]                    allowedOrigins;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler,
                           JwtHandshakeInterceptor jwtHandshakeInterceptor,
                           LobbyWebSocketHandler lobbyWebSocketHandler,
                           LobbyJwtHandshakeInterceptor lobbyJwtHandshakeInterceptor,
                           @Value("${app.allowed-origins}") List<String> allowedOrigins) {
        this.gameWebSocketHandler         = gameWebSocketHandler;
        this.jwtHandshakeInterceptor      = jwtHandshakeInterceptor;
        this.lobbyWebSocketHandler        = lobbyWebSocketHandler;
        this.lobbyJwtHandshakeInterceptor = lobbyJwtHandshakeInterceptor;
        this.allowedOrigins               = allowedOrigins.toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws/game/*")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins);

        registry.addHandler(lobbyWebSocketHandler, "/ws/lobby")
                .addInterceptors(lobbyJwtHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
