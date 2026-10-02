package com.chess.application;

import com.chess.api.dto.MatchmakingDto;
import com.chess.infrastructure.api.dto.CreateGameRequest;
import com.chess.infrastructure.websocket.LobbySessionManager;
import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import com.chess.persistence.entity.MatchmakingQueueEntity;
import com.chess.persistence.repository.MatchmakingQueueRepository;
import com.chess.persistence.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Manages the matchmaking queue for random online games.
 *
 * Players join the queue via REST. A @Scheduled task runs every second,
 * scans the queue per time-control type, and pairs players with similar ELO.
 * The ELO window expands the longer a player waits (200 + 100 per 30s, capped at 600).
 */
@Service
public class MatchmakingService {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);

    private final MatchmakingQueueRepository queueRepo;
    private final UserRepository             userRepo;
    private final GameApplicationService     engineService;
    private final GamePersistenceService     persistenceService;
    private final LobbySessionManager        lobbySessionManager;
    private final TransactionTemplate        tx;

    /** A pairing that has been committed, ready to announce. */
    private record Match(String gameId, MatchmakingQueueEntity white, MatchmakingQueueEntity black) {}

    public MatchmakingService(MatchmakingQueueRepository queueRepo,
                               UserRepository userRepo,
                               GameApplicationService engineService,
                               GamePersistenceService persistenceService,
                               LobbySessionManager lobbySessionManager,
                               TransactionTemplate tx) {
        this.tx                 = tx;
        this.queueRepo          = queueRepo;
        this.userRepo           = userRepo;
        this.engineService      = engineService;
        this.persistenceService = persistenceService;
        this.lobbySessionManager = lobbySessionManager;
    }

    // ── Join / Leave ──────────────────────────────────────────────────────────

    @Transactional
    public MatchmakingDto.QueueStatus joinQueue(UUID userId, MatchmakingDto.JoinQueueRequest req) {
        // If already queued, return existing entry
        var existing = queueRepo.findByUserIdAndMatchedFalse(userId);
        if (existing.isPresent()) {
            var e = existing.get();
            return toStatus(e);
        }

        var user = userRepo.findById(userId).orElseThrow();
        TimeControlKind tcType = TimeControlKind.valueOf(req.timeControlType());

        var entry = new MatchmakingQueueEntity();
        entry.setUserId(userId);
        entry.setUsername(user.getUsername());
        entry.setElo(user.getElo());
        entry.setTimeControlType(tcType);
        entry.setTimeControlInitialMs(req.timeControlInitialMs());
        entry.setTimeControlIncrementMs(req.timeControlIncrementMs());
        entry = queueRepo.save(entry);

        log.info("User {} joined matchmaking queue (ELO {}, {})", user.getUsername(), user.getElo(), tcType);
        return toStatus(entry);
    }

    @Transactional
    public void leaveQueue(UUID userId) {
        queueRepo.deleteByUserId(userId);
    }

    public Optional<MatchmakingDto.QueueStatus> getQueueStatus(UUID userId) {
        return queueRepo.findByUserIdAndMatchedFalse(userId).map(this::toStatus);
    }

    // ── Scheduled pairing ─────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 1000)
    public void runMatchmakingCycle() {
        for (TimeControlKind tc : TimeControlKind.values()) {
            try {
                pairForTimeControl(tc);
            } catch (Exception e) {
                log.warn("Matchmaking cycle error for {}: {}", tc, e.getMessage());
            }
        }
    }

    private void pairForTimeControl(TimeControlKind tc) {
        List<MatchmakingQueueEntity> candidates = queueRepo.findUnmatchedByTimeControl(tc);
        if (candidates.size() < 2) return;

        Set<UUID> processed = new HashSet<>();
        List<long[]> pairs = new ArrayList<>(); // pairs of index pairs

        for (int i = 0; i < candidates.size(); i++) {
            var a = candidates.get(i);
            if (processed.contains(a.getUserId())) continue;

            long waitSeconds = Duration.between(a.getJoinedAt(), Instant.now()).getSeconds();
            int eloWindow = Math.min(200 + (int)(waitSeconds / 30) * 100, 600);

            for (int j = i + 1; j < candidates.size(); j++) {
                var b = candidates.get(j);
                if (processed.contains(b.getUserId())) continue;
                // Same clock too: the game is played with one time control, so two
                // players who asked for different ones must not be paired
                boolean sameClock = a.getTimeControlInitialMs() == b.getTimeControlInitialMs()
                                 && a.getTimeControlIncrementMs() == b.getTimeControlIncrementMs();
                if (sameClock && Math.abs(a.getElo() - b.getElo()) <= eloWindow) {
                    processed.add(a.getUserId());
                    processed.add(b.getUserId());
                    pairs.add(new long[]{i, j});
                    break;
                }
            }
        }

        for (long[] pair : pairs) {
            var a = candidates.get((int) pair[0]);
            var b = candidates.get((int) pair[1]);
            try {
                // The game is committed before anyone hears about it: a player who
                // opens it at once must find it in the database
                Match match = tx.execute(status -> createOnlineGame(a, b));
                notifyMatch(match);
            } catch (Exception e) {
                log.error("Failed to create online game for {} vs {}: {}",
                          a.getUsername(), b.getUsername(), e.getMessage());
            }
        }
    }

    /** Pairs two queued players and stores their game. Runs inside the pairing transaction. */
    private Match createOnlineGame(MatchmakingQueueEntity p1, MatchmakingQueueEntity p2) {
        // Mark as matched to prevent double-pairing
        p1.setMatched(true);
        p2.setMatched(true);
        queueRepo.save(p1);
        queueRepo.save(p2);

        // Random color assignment
        boolean p1IsWhite = new Random().nextBoolean();
        MatchmakingQueueEntity white = p1IsWhite ? p1 : p2;
        MatchmakingQueueEntity black = p1IsWhite ? p2 : p1;

        // Create in-memory engine session (no AI)
        var engineResp = engineService.createGame(new CreateGameRequest(null, "NONE", 1));
        String gameId = engineResp.gameId();

        // Persist online game with both players
        persistenceService.persistNewOnlineGame(
            gameId,
            white.getUserId(), white.getUsername(), white.getElo(),
            black.getUserId(), black.getUsername(), black.getElo(),
            p1.getTimeControlType(), p1.getTimeControlInitialMs(), p1.getTimeControlIncrementMs()
        );

        // Clean up queue entries
        queueRepo.deleteById(p1.getId());
        queueRepo.deleteById(p2.getId());

        return new Match(gameId, white, black);
    }

    /** Tells both players over the lobby socket. */
    private void notifyMatch(Match match) {
        String gameId = match.gameId();
        var white = match.white();
        var black = match.black();
        var payloadWhite = new MatchmakingDto.MatchFoundPayload(
            gameId, black.getUsername(), black.getElo(), "white",
            white.getTimeControlType().name(), white.getTimeControlInitialMs(), white.getTimeControlIncrementMs()
        );
        var payloadBlack = new MatchmakingDto.MatchFoundPayload(
            gameId, white.getUsername(), white.getElo(), "black",
            black.getTimeControlType().name(), black.getTimeControlInitialMs(), black.getTimeControlIncrementMs()
        );
        lobbySessionManager.sendToUser(white.getUserId().toString(), "MATCH_FOUND", payloadWhite);
        lobbySessionManager.sendToUser(black.getUserId().toString(), "MATCH_FOUND", payloadBlack);

        log.info("Online game {} created: {} (white) vs {} (black)", gameId, white.getUsername(), black.getUsername());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private MatchmakingDto.QueueStatus toStatus(MatchmakingQueueEntity e) {
        long waitSeconds = Duration.between(e.getJoinedAt(), Instant.now()).getSeconds();
        return new MatchmakingDto.QueueStatus(
            e.getId().toString(),
            e.isMatched() ? "matched" : "queued",
            e.getJoinedAt(),
            Math.max(0, 30 - (int) waitSeconds) // rough estimate
        );
    }
}
