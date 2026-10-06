package com.chess.infrastructure.websocket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pings every open socket. A quiet socket carries no traffic, and a reverse proxy closes a
 * connection that has been silent for a minute (nginx's default), which looked to the other
 * player like a disconnect. The browser answers a ping on its own.
 */
@Component
public class SocketKeepAlive {

    private final WebSocketSessionManager gameSockets;
    private final LobbySessionManager lobbySockets;

    public SocketKeepAlive(WebSocketSessionManager gameSockets, LobbySessionManager lobbySockets) {
        this.gameSockets  = gameSockets;
        this.lobbySockets = lobbySockets;
    }

    @Scheduled(fixedDelay = 25_000)
    public void ping() {
        gameSockets.pingAll();
        lobbySockets.pingAll();
    }
}
