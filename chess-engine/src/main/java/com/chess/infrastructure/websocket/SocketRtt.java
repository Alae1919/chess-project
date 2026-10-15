package com.chess.infrastructure.websocket;

import com.chess.infrastructure.metrics.ChessMeters;
import io.micrometer.core.instrument.Timer;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;

import java.nio.ByteBuffer;
import java.time.Duration;

/**
 * Round trip of the keepalive pings. Each ping carries the time it was sent; the browser echoes
 * a ping's payload in its pong on its own, so the pong says how long the trip took, with no code
 * on the client.
 */
final class SocketRtt {

    private static final Timer GAME  = timer("game");
    private static final Timer LOBBY = timer("lobby");

    private SocketRtt() { }

    private static Timer timer(String socket) {
        return ChessMeters.timer("chess.ws.rtt", "Round trip of a keepalive ping to the browser and back", "socket", socket);
    }

    static PingMessage ping() {
        return new PingMessage(ByteBuffer.allocate(Long.BYTES).putLong(0, System.nanoTime()));
    }

    static void recordGame(PongMessage pong)  { record(GAME, pong); }
    static void recordLobby(PongMessage pong) { record(LOBBY, pong); }

    private static void record(Timer timer, PongMessage pong) {
        ByteBuffer payload = pong.getPayload();
        if (payload.remaining() != Long.BYTES) return; // not one of our pings
        long elapsed = System.nanoTime() - payload.getLong(payload.position());
        // a pong from before a restart, or a garbled one: no trip takes that long
        if (elapsed > 0 && elapsed < Duration.ofMinutes(1).toNanos()) timer.record(Duration.ofNanos(elapsed));
    }
}
