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
    private final SafeSockets safe = new SafeSockets();
    private final ObjectMapper objectMapper;

    public WebSocketSessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(String gameId, WebSocketSession session) {
        safe.add(session);
        sessions.computeIfAbsent(gameId, id -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(session);
    }

    public void unregister(String gameId, WebSocketSession session) {
        safe.remove(session);
        Set<WebSocketSession> gameSessions = sessions.get(gameId);
        if (gameSessions != null) {
            gameSessions.remove(session);
            if (gameSessions.isEmpty()) {
                sessions.remove(gameId, gameSessions);
            }
        }
    }

    /** Whether {@code userId} has an open socket on this game (they may have several tabs). */
    public boolean isConnected(String gameId, String userId) {
        Set<WebSocketSession> gameSessions = sessions.get(gameId);
        if (gameSessions == null) return false;
        return gameSessions.stream().anyMatch(s ->
            s.isOpen() && userId.equals(s.getAttributes().get("userId")));
    }

    /** Whether anyone has an open socket on this game. */
    public boolean hasAnyConnection(String gameId) {
        Set<WebSocketSession> gameSessions = sessions.get(gameId);
        return gameSessions != null && gameSessions.stream().anyMatch(WebSocketSession::isOpen);
    }

    /** Sends a ping to every open socket: traffic on an idle one keeps a proxy from closing it. */
    public void pingAll() {
        for (Set<WebSocketSession> gameSessions : sessions.values()) {
            for (WebSocketSession session : gameSessions) {
                if (!session.isOpen()) continue;
                try {
                    safe.forSending(session).sendMessage(new org.springframework.web.socket.PingMessage());
                } catch (Exception ignored) {
                    // closed meanwhile
                }
            }
        }
    }

    /** Sends one event to one socket. */
    public void send(WebSocketSession session, String type, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("type", type, "payload", payload));
            if (session.isOpen()) safe.forSending(session).sendMessage(new TextMessage(json));
        } catch (Exception ignored) {
            // the socket closed, or the payload could not be written: nothing to deliver to
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
                    safe.forSending(session).sendMessage(message);
                } catch (Exception ignored) {
                    // session may have closed between the isOpen check and sendMessage
                }
            }
        }
    }
}
