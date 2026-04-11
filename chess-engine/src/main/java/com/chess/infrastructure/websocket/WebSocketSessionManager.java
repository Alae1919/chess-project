package com.chess.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public WebSocketSessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(String gameId, WebSocketSession session) {
        sessions.computeIfAbsent(gameId, id -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(session);
    }

    public void unregister(String gameId, WebSocketSession session) {
        Set<WebSocketSession> gameSessions = sessions.get(gameId);
        if (gameSessions != null) {
            gameSessions.remove(session);
            if (gameSessions.isEmpty()) {
                sessions.remove(gameId, gameSessions);
            }
        }
    }

    public void broadcast(String gameId, String type, Object payload) {
        Set<WebSocketSession> gameSessions = sessions.get(gameId);
        if (gameSessions == null || gameSessions.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of("type", type, "payload", payload));
        } catch (Exception e) {
            return;
        }

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : gameSessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (Exception ignored) {
                    // session may have closed between the isOpen check and sendMessage
                }
            }
        }
    }
}
