package com.chess.application;

import com.chess.infrastructure.api.dto.GameStateResponse;
import com.chess.infrastructure.websocket.LobbySessionManager;
import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import com.chess.persistence.entity.MatchmakingQueueEntity;
import com.chess.persistence.repository.MatchmakingQueueRepository;
import com.chess.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MatchmakingService — pairing")
class MatchmakingServiceTest {

    private MatchmakingQueueRepository queue;
    private GameApplicationService engine;
    private GamePersistenceService persistence;
    private LobbySessionManager lobby;
    private TransactionTemplate tx;
    private MatchmakingService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        queue       = mock(MatchmakingQueueRepository.class);
        engine      = mock(GameApplicationService.class);
        persistence = mock(GamePersistenceService.class);
        lobby       = mock(LobbySessionManager.class);
        tx          = mock(TransactionTemplate.class);
        // run the callback straight away, as a real transaction would
        when(tx.execute(any())).thenAnswer(call -> ((TransactionCallback<Object>) call.getArgument(0)).doInTransaction(null));
        when(engine.createGame(any())).thenReturn(new GameStateResponse(
            "game-1", "fen", "WHITE", "ONGOING", null, List.of(), List.of(), List.of()));
        service = new MatchmakingService(queue, mock(UserRepository.class), engine, persistence, lobby, tx);
    }

    private static MatchmakingQueueEntity queued(String name, int elo, long initialMs, long incrementMs) {
        var e = new MatchmakingQueueEntity();
        e.setUserId(UUID.randomUUID());
        e.setUsername(name);
        e.setElo(elo);
        e.setTimeControlType(TimeControlKind.blitz);
        e.setTimeControlInitialMs(initialMs);
        e.setTimeControlIncrementMs(incrementMs);
        return e;
    }

    private void queueUp(MatchmakingQueueEntity... entries) {
        when(queue.findUnmatchedByTimeControl(TimeControlKind.blitz)).thenReturn(List.of(entries));
    }

    @Test
    @DisplayName("two players with the same clock and similar ratings get a game")
    void pairsCompatiblePlayers() {
        queueUp(queued("ann", 1200, 300_000, 2_000), queued("bob", 1250, 300_000, 2_000));

        service.runMatchmakingCycle();

        verify(persistence).persistNewOnlineGame(eq("game-1"), any(), any(), any(), any(), any(), any(),
            eq(TimeControlKind.blitz), eq(300_000L), eq(2_000L));
        verify(lobby, times(2)).sendToUser(any(), eq("MATCH_FOUND"), any());
    }

    @Test
    @DisplayName("players who asked for different clocks are not paired")
    void differentClocksStayApart() {
        queueUp(queued("ann", 1200, 180_000, 0), queued("bob", 1200, 300_000, 0));

        service.runMatchmakingCycle();

        verify(persistence, never()).persistNewOnlineGame(any(), any(), any(), any(), any(), any(), any(), any(),
            anyLong(), anyLong());
        verify(lobby, never()).sendToUser(any(), any(), any());
    }

    @Test
    @DisplayName("players far apart in rating are not paired")
    void ratingWindow() {
        queueUp(queued("ann", 1200, 300_000, 0), queued("bob", 1900, 300_000, 0));

        service.runMatchmakingCycle();

        verify(lobby, never()).sendToUser(any(), any(), any());
    }

    @Test
    @DisplayName("players hear about the game only after it is committed")
    void announcesAfterTheCommit() {
        queueUp(queued("ann", 1200, 300_000, 0), queued("bob", 1200, 300_000, 0));

        service.runMatchmakingCycle();

        InOrder order = inOrder(tx, lobby);
        order.verify(tx).execute(any());
        order.verify(lobby, times(2)).sendToUser(any(), eq("MATCH_FOUND"), any());
    }

    @Test
    @DisplayName("a pairing that fails to store tells nobody")
    void failedPairingIsSilent() {
        queueUp(queued("ann", 1200, 300_000, 0), queued("bob", 1200, 300_000, 0));
        doThrow(new IllegalStateException("db down")).when(persistence)
            .persistNewOnlineGame(any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyLong());

        service.runMatchmakingCycle();

        verify(lobby, never()).sendToUser(any(), any(), any());
    }
}
