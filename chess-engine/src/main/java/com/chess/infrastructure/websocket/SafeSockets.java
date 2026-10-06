package com.chess.infrastructure.websocket;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps sockets so that several threads can send on one safely.
 *
 * A raw socket refuses a message while another is being written, and the HTTP threads, the
 * schedulers and the countdowns all send to the same players. Without this the refusal was
 * swallowed and the message (a GAME_OVER, say) never arrived. The wrapper queues the extra
 * messages and writes them in turn; a client too slow to take them is cut off rather than
 * holding a thread.
 */
final class SafeSockets {

    /** How long one message may take to write before the socket is given up on. */
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    /** How many bytes may wait to be written before the socket is given up on. */
    private static final int BUFFER_LIMIT_BYTES = 512 * 1024;

    private final Map<WebSocketSession, WebSocketSession> wrapped = new ConcurrentHashMap<>();

    /** Starts keeping {@code socket} safe to send on. */
    void add(WebSocketSession socket) {
        wrapped.computeIfAbsent(socket,
            s -> new ConcurrentWebSocketSessionDecorator(s, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES));
    }

    void remove(WebSocketSession socket) {
        wrapped.remove(socket);
    }

    /** The socket to send on: the safe wrapper if there is one, else the socket itself. */
    WebSocketSession forSending(WebSocketSession socket) {
        return wrapped.getOrDefault(socket, socket);
    }
}
