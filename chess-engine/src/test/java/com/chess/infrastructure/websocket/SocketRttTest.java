package com.chess.infrastructure.websocket;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.PongMessage;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SocketRtt: the keepalive ping's round trip")
class SocketRttTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void watch() { Metrics.globalRegistry.add(registry); }

    @AfterEach
    void stopWatching() { Metrics.globalRegistry.remove(registry); }

    private double trips(String socket) {
        var timer = registry.find("chess.ws.rtt").tag("socket", socket).timer();
        return timer == null ? 0 : timer.count();
    }

    @Test
    @DisplayName("a pong that echoes our ping is timed")
    void echoedPingIsTimed() {
        ByteBuffer sent = SocketRtt.ping().getPayload();
        SocketRtt.recordGame(new PongMessage(sent));
        assertThat(trips("game")).isEqualTo(1);
    }

    @Test
    @DisplayName("a pong with some other payload is not")
    void otherPongsAreIgnored() {
        SocketRtt.recordLobby(new PongMessage(ByteBuffer.wrap(new byte[] {1, 2, 3})));
        SocketRtt.recordLobby(new PongMessage(ByteBuffer.allocate(Long.BYTES).putLong(0, System.nanoTime() + 1_000_000_000L)));
        assertThat(trips("lobby")).isZero();
    }
}
