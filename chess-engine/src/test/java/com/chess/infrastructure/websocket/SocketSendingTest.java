package com.chess.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Sockets — sending from several threads, and staying alive")
class SocketSendingTest {

    /**
     * A socket that, like the real one, refuses a second message while one is still being
     * written, and counts what it was given.
     */
    private static final class StrictSocket {
        final WebSocketSession session = mock(WebSocketSession.class);
        final AtomicInteger writing = new AtomicInteger();
        final AtomicInteger overlaps = new AtomicInteger();
        final List<WebSocketMessage<?>> delivered = Collections.synchronizedList(new ArrayList<>());

        StrictSocket(String userId) throws Exception {
            when(session.isOpen()).thenReturn(true);
            when(session.getAttributes()).thenReturn(Map.of("userId", userId));
            doAnswer(call -> {
                if (writing.incrementAndGet() > 1) overlaps.incrementAndGet();
                try {
                    Thread.sleep(3);               // writing takes a moment
                } finally {
                    writing.decrementAndGet();
                }
                delivered.add(call.getArgument(0));
                return null;
            }).when(session).sendMessage(any());
        }

        long texts() { return delivered.stream().filter(m -> m instanceof TextMessage).count(); }
        long pings() { return delivered.stream().filter(m -> m instanceof PingMessage).count(); }
    }

    /** Runs {@code action} on 8 threads at once, 10 times each. */
    private static void hammer(Runnable action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        for (int t = 0; t < 8; t++) {
            pool.submit(() -> {
                try { go.await(); } catch (InterruptedException e) { return; }
                for (int i = 0; i < 10; i++) action.run();
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("game events sent from several threads are all delivered, one at a time")
    void gameBroadcastsDoNotOverlap() throws Exception {
        var sockets = new WebSocketSessionManager(new ObjectMapper());
        var socket = new StrictSocket("u1");
        sockets.register("game-1", socket.session);

        hammer(() -> sockets.broadcast("game-1", "MOVE_MADE", Map.of("n", 1)));

        await().atMost(Duration.ofSeconds(10)).until(() -> socket.texts() == 80);
        assertEquals(0, socket.overlaps.get(), "messages written on top of each other");
    }

    @Test
    @DisplayName("lobby events sent from several threads are all delivered, one at a time")
    void lobbyMessagesDoNotOverlap() throws Exception {
        var lobby = new LobbySessionManager(new ObjectMapper());
        var socket = new StrictSocket("u1");
        lobby.register("u1", socket.session);

        hammer(() -> lobby.sendToUser("u1", "INVITE_RECEIVED", Map.of("n", 1)));

        await().atMost(Duration.ofSeconds(10)).until(() -> socket.texts() == 80);
        assertEquals(0, socket.overlaps.get(), "messages written on top of each other");
    }

    @Test
    @DisplayName("a socket is still found and removed by the session the handler knows")
    void unregisterFindsTheSocket() throws Exception {
        var sockets = new WebSocketSessionManager(new ObjectMapper());
        var lobby = new LobbySessionManager(new ObjectMapper());
        var socket = new StrictSocket("u1");
        sockets.register("game-1", socket.session);
        lobby.register("u1", socket.session);
        assertTrue(sockets.isConnected("game-1", "u1"));
        assertTrue(lobby.isConnected("u1"));

        sockets.unregister("game-1", socket.session);
        lobby.unregister("u1", socket.session);

        assertFalse(sockets.hasAnyConnection("game-1"));
        assertFalse(lobby.isConnected("u1"));
    }

    @Test
    @DisplayName("a ping goes to every open socket, so an idle one is not closed by a proxy")
    void pingReachesOpenSockets() throws Exception {
        var sockets = new WebSocketSessionManager(new ObjectMapper());
        var lobby = new LobbySessionManager(new ObjectMapper());
        var inGame = new StrictSocket("u1");
        var inLobby = new StrictSocket("u2");
        var closed = new StrictSocket("u3");
        when(closed.session.isOpen()).thenReturn(false);
        sockets.register("game-1", inGame.session);
        lobby.register("u2", inLobby.session);
        sockets.register("game-1", closed.session);

        sockets.pingAll();
        lobby.pingAll();

        assertEquals(1, inGame.pings());
        assertEquals(1, inLobby.pings());
        assertEquals(0, closed.pings());
    }
}
