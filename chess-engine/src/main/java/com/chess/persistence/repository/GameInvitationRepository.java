package com.chess.persistence.repository;

import com.chess.persistence.entity.DatabaseEnums.InvitationStatus;
import com.chess.persistence.entity.GameInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameInvitationRepository extends JpaRepository<GameInvitationEntity, UUID> {

    List<GameInvitationEntity> findByInviteeIdAndStatus(UUID inviteeId, InvitationStatus status);

    List<GameInvitationEntity> findByInviterIdAndStatus(UUID inviterId, InvitationStatus status);

    Optional<GameInvitationEntity> findByIdAndInviteeId(UUID id, UUID inviteeId);

    Optional<GameInvitationEntity> findByIdAndInviterId(UUID id, UUID inviterId);

    List<GameInvitationEntity> findByStatusAndExpiresAtBefore(InvitationStatus status, Instant cutoff);

    /**
     * Accepts the invitation if it is still pending and unexpired, in one statement: of two
     * answers arriving together, the database lets exactly one change the row.
     *
     * @return 1 if this call accepted it, 0 if someone else already answered, or it ran out
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE GameInvitationEntity i SET i.status = :accepted "
         + "WHERE i.id = :id AND i.inviteeId = :inviteeId AND i.status = :pending AND i.expiresAt > :now")
    int accept(@Param("id") UUID id, @Param("inviteeId") UUID inviteeId, @Param("now") Instant now,
               @Param("accepted") InvitationStatus accepted, @Param("pending") InvitationStatus pending);
}
