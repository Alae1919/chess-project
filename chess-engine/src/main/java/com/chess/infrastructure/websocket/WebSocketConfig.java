package com.chess.infrastructure.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler        gameWebSocketHandler;
    private final JwtHandshakeInterceptor     jwtHandshakeInterceptor;
    private final LobbyWebSocketHandler       lobbyWebSocketHandler;
    private final LobbyJwtHandshakeInterceptor lobbyJwtHandshakeInterceptor;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler,
                           JwtHandshakeInterceptor jwtHandshakeInterceptor,
                           LobbyWebSocketHandler lobbyWebSocketHandler,
                           LobbyJwtHandshakeInterceptor lobbyJwtHandshakeInterceptor) {
        this.gameWebSocketHandler         = gameWebSocketHandler;
        this.jwtHandshakeInterceptor      = jwtHandshakeInterceptor;
        this.lobbyWebSocketHandler        = lobbyWebSocketHandler;
        this.lobbyJwtHandshakeInterceptor = lobbyJwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws/game/*")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");

        registry.addHandler(lobbyWebSocketHandler, "/ws/lobby")
                .addInterceptors(lobbyJwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
