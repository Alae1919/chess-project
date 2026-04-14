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

    @Modifying
    @Query(value = "UPDATE game_invitations SET status = 'expired' WHERE status = 'pending' AND expires_at < :now",
           nativeQuery = true)
    int expireOldInvitations(@Param("now") Instant now);
}
