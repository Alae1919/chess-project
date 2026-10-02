package com.chess.application;

import com.chess.api.dto.InvitationDto;
import com.chess.infrastructure.api.dto.GameStateResponse;
import com.chess.infrastructure.websocket.LobbySessionManager;
import com.chess.persistence.entity.DatabaseEnums.InvitationStatus;
import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import com.chess.persistence.entity.GameInvitationEntity;
import com.chess.persistence.entity.UserEntity;
import com.chess.persistence.repository.GameInvitationRepository;
import com.chess.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("InvitationService — colours and expiry")
class InvitationServiceTest {

    private final UUID inviterId = UUID.randomUUID();
    private final UUID inviteeId = UUID.randomUUID();

    private GameInvitationRepository invitations;
    private UserRepository users;
    private GameApplicationService engine;
    private GamePersistenceService persistence;
    private LobbySessionManager lobby;
    private InvitationService service;

    @BeforeEach
    void setUp() {
        invitations = mock(GameInvitationRepository.class);
        users       = mock(UserRepository.class);
        engine      = mock(GameApplicationService.class);
        persistence = mock(GamePersistenceService.class);
        lobby       = mock(LobbySessionManager.class);
        service     = new InvitationService(invitations, users, engine, persistence, lobby);

        when(users.findById(inviterId)).thenReturn(Optional.of(user(inviterId, "ann")));
        when(users.findById(inviteeId)).thenReturn(Optional.of(user(inviteeId, "bob")));
        when(engine.createGame(any())).thenReturn(new GameStateResponse(
            UUID.randomUUID().toString(), "fen", "WHITE", "ONGOING", null, List.of(), List.of(), List.of()));
    }

    /** Entities get their id from the database; tests set it directly. */
    private static void setId(Object entity, UUID id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static UserEntity user(UUID id, String name) {
        var u = new UserEntity();
        u.setUsername(name);
        u.setEmail(name + "@example.com");
        setId(u, id);
        return u;
    }

    private GameInvitationEntity pending(String inviterColor) {
        var i = new GameInvitationEntity();
        i.setInviterId(inviterId);
        i.setInviterUsername("ann");
        i.setInviteeId(inviteeId);
        i.setInviteeUsername("bob");
        i.setTimeControlType(TimeControlKind.blitz);
        i.setTimeControlInitialMs(300_000);
        i.setTimeControlIncrementMs(0);
        i.setInviterColor(inviterColor);
        setId(i, UUID.randomUUID());
        return i;
    }

    /** Accepts the invitation and returns who ended up with the white pieces. */
    private UUID acceptAndGetWhite(GameInvitationEntity invitation) {
        var id = invitation.getId();
        when(invitations.findByIdAndInviteeId(id, inviteeId)).thenReturn(Optional.of(invitation));

        service.respondToInvitation(inviteeId, id, "accept");

        var white = ArgumentCaptor.forClass(UUID.class);
        verify(persistence).persistNewOnlineGame(any(), white.capture(), any(), any(), any(), any(), any(),
            any(), anyLong(), anyLong());
        return white.getValue();
    }

    @Test
    @DisplayName("an inviter who asks for black plays black")
    void inviterChoosesBlack() {
        assertEquals(inviteeId, acceptAndGetWhite(pending("black")));
    }

    @Test
    @DisplayName("an inviter who asks for white plays white")
    void inviterChoosesWhite() {
        assertEquals(inviterId, acceptAndGetWhite(pending("white")));
    }

    @Test
    @DisplayName("unanswered invitations expire and both players are told")
    void expiryNotifiesBothPlayers() {
        var stale = pending(null);
        when(invitations.findByStatusAndExpiresAtBefore(eq(InvitationStatus.pending), any(Instant.class)))
            .thenReturn(List.of(stale));

        service.expireOldInvitations();

        assertEquals(InvitationStatus.expired, stale.getStatus());
        verify(invitations).save(stale);
        verify(lobby).sendToUser(eq(inviterId.toString()), eq("INVITE_EXPIRED"), any());
        verify(lobby).sendToUser(eq(inviteeId.toString()), eq("INVITE_EXPIRED"), any());
    }

    @Test
    @DisplayName("nothing to expire, nobody is told")
    void quietWhenNothingExpired() {
        when(invitations.findByStatusAndExpiresAtBefore(any(), any())).thenReturn(List.of());

        service.expireOldInvitations();

        verify(lobby, never()).sendToUser(any(), any(), any());
    }

    @Test
    @DisplayName("the request accepts only white or black as a colour")
    void colourIsValidated() throws Exception {
        var request = new InvitationDto.SendInvitationRequest("bob", "blitz", 300_000L, 0, "purple");
        var violations = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator().validate(request);
        assertEquals(1, violations.size());
    }
}
