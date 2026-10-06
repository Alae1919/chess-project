package com.chess.application;

import com.chess.ChessApplication;
import com.chess.api.dto.InvitationDto;
import com.chess.infrastructure.websocket.LobbySessionManager;
import com.chess.persistence.entity.UserEntity;
import com.chess.persistence.entity.UserPreferencesEntity;
import com.chess.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(classes = ChessApplication.class)
@DisplayName("InvitationService — accepting an invitation")
class InvitationAcceptanceTest {

    @Autowired InvitationService invitations;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @SpyBean   LobbySessionManager lobby;

    private UUID ensureUser(String username) {
        return users.findByUsername(username).orElseGet(() -> {
            var u = new UserEntity();
            u.setUsername(username);
            u.setEmail(username + "@example.com");
            u.setPasswordHash("unused");
            u.setPreferences(UserPreferencesEntity.defaultsFor(u));
            return users.save(u);
        }).getId();
    }

    private UUID invite(UUID from, String toUsername) {
        var sent = invitations.sendInvitation(from, new InvitationDto.SendInvitationRequest(
            toUsername, "blitz", 300_000L, 0, "white"));
        return UUID.fromString(sent.invitationId());
    }

    private int gamesBetween(UUID a, UUID b) {
        return jdbc.queryForObject(
            "select count(*) from games where (white_user_id = ? and black_user_id = ?) "
          + "or (white_user_id = ? and black_user_id = ?)", Integer.class, a, b, b, a);
    }

    @RepeatedTest(5)
    @DisplayName("accepting twice at once starts one game, and the second answer is refused")
    void doubleAcceptCreatesOneGame() throws Exception {
        UUID inviter = ensureUser("ia_test_inviter");
        UUID invitee = ensureUser("ia_test_invitee");
        UUID invitation = invite(inviter, "ia_test_invitee");
        int gamesBefore = gamesBetween(inviter, invitee);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Object> outcomes = Collections.synchronizedList(new ArrayList<>());
        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        go.await();
                        outcomes.add(invitations.respondToInvitation(invitee, invitation, "accept"));
                    } catch (Throwable t) {
                        outcomes.add(t);
                    }
                });
            }
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        long refused = outcomes.stream().filter(o -> o instanceof IllegalStateException).count();
        assertEquals(1, refused, "outcomes: " + outcomes);
        assertEquals(gamesBefore + 1, gamesBetween(inviter, invitee), "games created");
    }

    @Test
    @DisplayName("the players are told only once the game can be found by anyone")
    void matchFoundComesAfterTheGameIsStored() {
        UUID inviter = ensureUser("ia_test_inviter2");
        UUID invitee = ensureUser("ia_test_invitee2");
        UUID invitation = invite(inviter, "ia_test_invitee2");

        List<Integer> visibleWhenAnnounced = Collections.synchronizedList(new ArrayList<>());
        doAnswer(call -> {
            Object payload = call.getArgument(2);
            String gameId = payload.getClass().getSimpleName().equals("MatchFoundPayload")
                ? gameIdOf(payload) : null;
            // another database connection, so only committed rows are visible
            visibleWhenAnnounced.add(jdbc.queryForObject(
                "select count(*) from games where id = cast(? as uuid)", Integer.class, gameId));
            return null;
        }).when(lobby).sendToUser(any(), eq("MATCH_FOUND"), any());

        invitations.respondToInvitation(invitee, invitation, "accept");

        assertEquals(List.of(1, 1), visibleWhenAnnounced, "one announcement per player, game already stored");
    }

    private static String gameIdOf(Object matchFoundPayload) {
        try {
            return (String) matchFoundPayload.getClass().getMethod("gameId").invoke(matchFoundPayload);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
