package com.chess.persistence.repository;

import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import com.chess.persistence.entity.MatchmakingQueueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchmakingQueueRepository extends JpaRepository<MatchmakingQueueEntity, UUID> {

    Optional<MatchmakingQueueEntity> findByUserIdAndMatchedFalse(UUID userId);

    boolean existsByUserIdAndMatchedFalse(UUID userId);

    void deleteByUserId(UUID userId);

    List<MatchmakingQueueEntity> findByMatchedFalse();

    @Query("SELECT e FROM MatchmakingQueueEntity e WHERE e.matched = false " +
           "AND e.timeControlType = :tc ORDER BY e.joinedAt ASC")
    List<MatchmakingQueueEntity> findUnmatchedByTimeControl(@Param("tc") TimeControlKind tc);
}
