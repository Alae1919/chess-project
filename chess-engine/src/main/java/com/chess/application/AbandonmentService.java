package com.chess.application;

import com.chess.domain.model.Color;
import com.chess.infrastructure.websocket.WebSocketSessionManager;
import com.chess.persistence.entity.DatabaseEnums.GameMode;
import com.chess.persistence.entity.DatabaseEnums.GameStatus;
import com.chess.persistence.repository.GameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Online games need both players. When a player's last socket on a game closes
 * the opponent is told and a countdown starts; coming back in time cancels it,
 * otherwise the player who stayed wins.
 *
 * Clocks keep running meanwhile, so a timed game may end on time first. Only
 * online games are watched: AI and local games just wait for their one player.
 */
@Service
public class AbandonmentService {

    private static final Logger log = LoggerFactory.getLogger(AbandonmentService.class);

    /** Fewer moves than this and a game with nobody left in it is called off rather than scored. */
    private static final int MIN_MOVES_TO_SCORE = 2;

    private record Countdown(ScheduledFuture<?> task, Color color, long timeoutMs) {}

    private final WebSocketSessionManager sockets;
    private final GameRepository          games;
    private final GameApplicationService  engine;
    private final GamePersistenceService  persistence;
    private final TaskScheduler           scheduler;
    private final Duration                timeout;

    /** Running countdowns, keyed by game and user. */
    private final Map<String, Countdown> pending = new ConcurrentHashMap<>();

    @Autowired
    public AbandonmentService(WebSocketSessionManager sockets, GameRepository games,
                              GameApplicationService engine, GamePersistenceService persistence,
                              TaskScheduler scheduler,
                              @Value("${app.abandon-timeout-seconds}") long timeoutSeconds) {
        this(sockets, games, engine, persistence, scheduler, Duration.ofSeconds(timeoutSeconds));
    }

    AbandonmentService(WebSocketSessionManager sockets, GameRepository games,
                       GameApplicationService engine, GamePersistenceService persistence,
                       TaskScheduler scheduler, Duration timeout) {
        this.sockets     = sockets;
        this.games       = games;
        this.engine      = engine;
        this.persistence = persistence;
        this.scheduler   = scheduler;
        this.timeout     = timeout;
    }

    /** A socket opened on a game. */
    public void playerConnected(String gameId, String userId, WebSocketSession socket) {
        if (userId == null) return;

        Countdown back = pending.remove(key(gameId, userId));
        if (back != null) {
            back.task().cancel(false);
            sockets.broadcast(gameId, "OPPONENT_RECONNECTED", Map.of("color", colorName(back.color())));
        }

        // Someone arriving while the opponent is gone should see the countdown too
        pending.forEach((key, countdown) -> {
            if (key.startsWith(gameId + ":") && !key.equals(key(gameId, userId))) {
                sockets.send(socket, "OPPONENT_DISCONNECTED", disconnectedPayload(countdown));
            }
        });
    }

    /** A socket on a game closed. */
    public void playerDisconnected(String gameId, String userId) {
        if (userId == null || sockets.isConnected(gameId, userId)) return; // another tab is still open

        var game = parse(gameId).flatMap(games::findById).orElse(null);
        if (game == null || game.getMode() != GameMode.online || game.getStatus() != GameStatus.active) return;
        Color color = userId.equals(String.valueOf(game.getWhiteUserId())) ? Color.WHITE
                    : userId.equals(String.valueOf(game.getBlackUserId())) ? Color.BLACK : null;
        if (color == null) return;

        String key = key(gameId, userId);
        var task = scheduler.schedule(() -> expire(gameId, userId), Instant.now().plus(timeout));
        var countdown = new Countdown(task, color, timeout.toMillis());
        var previous = pending.put(key, countdown);
        if (previous != null) previous.task().cancel(false);

        sockets.broadcast(gameId, "OPPONENT_DISCONNECTED", disconnectedPayload(countdown));
    }

    private void expire(String gameId, String userId) {
        Countdown countdown = pending.remove(key(gameId, userId));
        if (countdown == null || sockets.isConnected(gameId, userId)) return;
        try {
            // Nobody connected means both players are gone, and this countdown is the one
            // that ran out first, so this player left first. A game that never really began
            // is called off; one that was played is lost by whoever left first.
            boolean opponentHere = sockets.hasAnyConnection(gameId);
            boolean calledOff = !opponentHere && engine.getMoveCount(gameId) < MIN_MOVES_TO_SCORE;
            var ended = calledOff ? engine.abort(gameId) : engine.abandon(gameId, countdown.color());

            ended.ifPresent(over -> {
                var game = persistence.toFullGameDto(gameId, over);
                sockets.broadcast(gameId, "GAME_OVER", game);
                log.info("Game {} {}: {} left", gameId, calledOff ? "aborted" : "ended",
                         colorName(countdown.color()));
            });
        } catch (RuntimeException e) {
            log.warn("Could not end abandoned game {}", gameId, e);
        }
    }

    private static Map<String, Object> disconnectedPayload(Countdown c) {
        return Map.of("color", colorName(c.color()), "timeoutMs", c.timeoutMs());
    }

    private static String colorName(Color c) { return c.name().toLowerCase(); }

    private static String key(String gameId, String userId) { return gameId + ":" + userId; }

    private static java.util.Optional<UUID> parse(String id) {
        try {
            return java.util.Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
