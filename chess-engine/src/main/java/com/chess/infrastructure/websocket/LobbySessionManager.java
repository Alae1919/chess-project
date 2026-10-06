package com.chess.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks one lobby WebSocket session per authenticated user.
 * Used to deliver pre-game notifications: MATCH_FOUND, INVITE_RECEIVED, etc.
 */
@Component
public class LobbySessionManager {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final SafeSockets safe = new SafeSockets();
    private final ObjectMapper objectMapper;

    public LobbySessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(String userId, WebSocketSession session) {
        safe.add(session);
        sessions.put(userId, session);
    }

    /**
     * Forgets {@code session}, but only if it is still the user's current one: a
     * second tab (or a reconnect) replaces the first, and the first closing
     * afterwards must not knock the newer socket out.
     */
    public void unregister(String userId, WebSocketSession session) {
        safe.remove(session); // also when a newer tab already replaced it
        sessions.remove(userId, session);
    }

    /** Sends a ping to every open socket: traffic on an idle one keeps a proxy from closing it. */
    public void pingAll() {
        for (WebSocketSession session : sessions.values()) {
            if (!session.isOpen()) continue;
            try {
                safe.forSending(session).sendMessage(new org.springframework.web.socket.PingMessage());
            } catch (Exception ignored) {
                // closed meanwhile
            }
        }
    }

    public boolean isConnected(String userId) {
        var s = sessions.get(userId);
        return s != null && s.isOpen();
    }

    /**
     * Sends a typed JSON event to a specific user.
     * Silently no-ops if the user has no active lobby session.
     */
    public void sendToUser(String userId, String type, Object payload) {
        var session = sessions.get(userId);
        if (session == null || !session.isOpen()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of("type", type, "payload", payload));
        } catch (Exception e) {
            return;
        }

        try {
            safe.forSending(session).sendMessage(new TextMessage(json));
        } catch (Exception ignored) {
            // session may have closed between the isOpen check and sendMessage
        }
    }
}
