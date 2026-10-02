package com.chess.application;

import com.chess.api.dto.InvitationDto;
import com.chess.api.dto.MatchmakingDto;
import com.chess.infrastructure.api.dto.CreateGameRequest;
import com.chess.infrastructure.websocket.LobbySessionManager;
import com.chess.persistence.entity.DatabaseEnums.InvitationStatus;
import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import com.chess.persistence.entity.GameInvitationEntity;
import com.chess.persistence.repository.GameInvitationRepository;
import com.chess.persistence.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Manages friend game invitations (challenge flow).
 *
 * Inviter sends an invite; invitee is notified in real time via lobby WebSocket.
 * On acceptance a new online game is created and both players are notified.
 */
@Service
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

    private final GameInvitationRepository invitationRepo;
    private final UserRepository           userRepo;
    private final GameApplicationService   engineService;
    private final GamePersistenceService   persistenceService;
    private final LobbySessionManager      lobbySessionManager;

    public InvitationService(GameInvitationRepository invitationRepo,
                              UserRepository userRepo,
                              GameApplicationService engineService,
                              GamePersistenceService persistenceService,
                              LobbySessionManager lobbySessionManager) {
        this.invitationRepo     = invitationRepo;
        this.userRepo           = userRepo;
        this.engineService      = engineService;
        this.persistenceService = persistenceService;
        this.lobbySessionManager = lobbySessionManager;
    }

    // ── Send invitation ───────────────────────────────────────────────────────

    @Transactional
    public InvitationDto.InvitationResponse sendInvitation(UUID inviterId,
                                                            InvitationDto.SendInvitationRequest req) {
        var inviter = userRepo.findById(inviterId).orElseThrow();
        var invitee = userRepo.findByUsername(req.inviteeUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + req.inviteeUsername()));

        if (inviter.getId().equals(invitee.getId()))
            throw new IllegalArgumentException("Cannot invite yourself");

        TimeControlKind tcType = TimeControlKind.valueOf(req.timeControlType());

        var invitation = new GameInvitationEntity();
        invitation.setInviterId(inviterId);
        invitation.setInviterUsername(inviter.getUsername());
        invitation.setInviteeId(invitee.getId());
        invitation.setInviteeUsername(invitee.getUsername());
        invitation.setTimeControlType(tcType);
        invitation.setTimeControlInitialMs(req.timeControlInitialMs());
        invitation.setTimeControlIncrementMs(req.timeControlIncrementMs());
        invitation.setInviterColor(req.inviterColor());
        invitation = invitationRepo.save(invitation);

        // Notify invitee in real time if they're connected to the lobby
        var payload = new InvitationDto.InviteReceivedPayload(
            invitation.getId().toString(),
            inviter.getUsername(),
            invitee.getUsername(),
            inviter.getElo(),
            tcType.name(),
            req.timeControlInitialMs(),
            req.timeControlIncrementMs(),
            "pending",
            invitation.getCreatedAt(),
            invitation.getExpiresAt()
        );
        lobbySessionManager.sendToUser(invitee.getId().toString(), "INVITE_RECEIVED", payload);

        log.info("Invitation {} sent from {} to {}", invitation.getId(),
                  inviter.getUsername(), invitee.getUsername());
        return toResponse(invitation);
    }

    // ── Respond to invitation ─────────────────────────────────────────────────

    @Transactional
    public Object respondToInvitation(UUID inviteeId, UUID invitationId, String response) {
        var invitation = invitationRepo.findByIdAndInviteeId(invitationId, inviteeId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));

        if (invitation.getStatus() != InvitationStatus.pending)
            throw new IllegalStateException("Invitation is no longer pending");

        if (Instant.now().isAfter(invitation.getExpiresAt())) {
            invitation.setStatus(InvitationStatus.expired);
            invitationRepo.save(invitation);
            throw new IllegalStateException("Invitation has expired");
        }

        if ("decline".equalsIgnoreCase(response)) {
            invitation.setStatus(InvitationStatus.declined);
            invitationRepo.save(invitation);
            lobbySessionManager.sendToUser(invitation.getInviterId().toString(),
                "INVITE_DECLINED", toResponse(invitation));
            return toResponse(invitation);
        }

        if ("accept".equalsIgnoreCase(response)) {
            invitation.setStatus(InvitationStatus.accepted);

            // The inviter's choice (a rematch swaps colours), else a coin toss
            boolean inviterIsWhite = invitation.getInviterColor() != null
                ? "white".equals(invitation.getInviterColor())
                : new Random().nextBoolean();
            UUID whiteId       = inviterIsWhite ? invitation.getInviterId()   : inviteeId;
            String whiteUser   = inviterIsWhite ? invitation.getInviterUsername() : invitation.getInviteeUsername();
            UUID blackId       = inviterIsWhite ? inviteeId                    : invitation.getInviterId();
            String blackUser   = inviterIsWhite ? invitation.getInviteeUsername() : invitation.getInviterUsername();

            var inviterEntity = userRepo.findById(invitation.getInviterId()).orElseThrow();
            var inviteeEntity = userRepo.findById(inviteeId).orElseThrow();
            Integer whiteElo = inviterIsWhite ? inviterEntity.getElo() : inviteeEntity.getElo();
            Integer blackElo = inviterIsWhite ? inviteeEntity.getElo() : inviterEntity.getElo();

            // Create engine session
            var engineResp = engineService.createGame(new CreateGameRequest(null, "NONE", 1));
            String gameId = engineResp.gameId();

            // Persist online game
            persistenceService.persistNewOnlineGame(
                gameId,
                whiteId, whiteUser, whiteElo,
                blackId, blackUser, blackElo,
                invitation.getTimeControlType(),
                invitation.getTimeControlInitialMs(),
                invitation.getTimeControlIncrementMs()
            );

            invitation.setGameId(UUID.fromString(gameId));
            invitationRepo.save(invitation);

            // Determine each player's color for their payload
            String inviterColor = inviterIsWhite ? "white" : "black";
            String inviteeColor = inviterIsWhite ? "black" : "white";

            var payloadInviter = new MatchmakingDto.MatchFoundPayload(
                gameId, invitation.getInviteeUsername(), inviteeEntity.getElo(), inviterColor,
                invitation.getTimeControlType().name(),
                invitation.getTimeControlInitialMs(), invitation.getTimeControlIncrementMs()
            );
            var payloadInvitee = new MatchmakingDto.MatchFoundPayload(
                gameId, invitation.getInviterUsername(), inviterEntity.getElo(), inviteeColor,
                invitation.getTimeControlType().name(),
                invitation.getTimeControlInitialMs(), invitation.getTimeControlIncrementMs()
            );
            lobbySessionManager.sendToUser(invitation.getInviterId().toString(), "MATCH_FOUND", payloadInviter);
            lobbySessionManager.sendToUser(inviteeId.toString(), "MATCH_FOUND", payloadInvitee);

            log.info("Invitation {} accepted — game {} created", invitationId, gameId);

            // Return the full game DTO
            return persistenceService.toFullGameDto(gameId, engineResp);
        }

        throw new IllegalArgumentException("response must be 'accept' or 'decline'");
    }

    // ── Cancel invitation (inviter) ───────────────────────────────────────────

    @Transactional
    public void cancelInvitation(UUID inviterId, UUID invitationId) {
        var invitation = invitationRepo.findByIdAndInviterId(invitationId, inviterId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));
        if (invitation.getStatus() != InvitationStatus.pending)
            throw new IllegalStateException("Invitation is no longer pending");
        invitation.setStatus(InvitationStatus.cancelled);
        invitationRepo.save(invitation);
        lobbySessionManager.sendToUser(invitation.getInviteeId().toString(),
            "INVITE_CANCELLED", toResponse(invitation));
    }

    // ── List pending received invitations ─────────────────────────────────────

    public List<InvitationDto.InvitationResponse> getPendingInvitations(UUID userId) {
        return invitationRepo.findByInviteeIdAndStatus(userId, InvitationStatus.pending)
                .stream()
                .filter(i -> Instant.now().isBefore(i.getExpiresAt()))
                .map(this::toResponse)
                .toList();
    }

    // ── Scheduled expiry ──────────────────────────────────────────────────────

    /** Expires invitations nobody answered, and tells both players so their screens clear. */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void expireOldInvitations() {
        var stale = invitationRepo.findByStatusAndExpiresAtBefore(InvitationStatus.pending, Instant.now());
        for (var invitation : stale) {
            invitation.setStatus(InvitationStatus.expired);
            invitationRepo.save(invitation);
            var response = toResponse(invitation);
            lobbySessionManager.sendToUser(invitation.getInviterId().toString(), "INVITE_EXPIRED", response);
            lobbySessionManager.sendToUser(invitation.getInviteeId().toString(), "INVITE_EXPIRED", response);
        }
        if (!stale.isEmpty()) log.debug("Expired {} stale invitation(s)", stale.size());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private InvitationDto.InvitationResponse toResponse(GameInvitationEntity e) {
        return new InvitationDto.InvitationResponse(
            e.getId().toString(),
            e.getInviterUsername(),
            e.getInviteeUsername(),
            e.getStatus().name(),
            e.getTimeControlType().name(),
            e.getTimeControlInitialMs(),
            e.getTimeControlIncrementMs(),
            e.getCreatedAt(),
            e.getExpiresAt()
        );
    }
}
