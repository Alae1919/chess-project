package com.chess.persistence.repository;

import com.chess.persistence.entity.GameEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface GameRepository extends JpaRepository<GameEntity, UUID> {

    @Query("SELECT g FROM GameEntity g WHERE "
         + "(g.whiteUserId = :userId OR g.blackUserId = :userId) "
         + "AND g.status = 'finished' "
         + "ORDER BY g.updatedAt DESC")
    Page<GameEntity> findMatchHistoryForUser(UUID userId, Pageable pageable);

    @Query("SELECT g FROM GameEntity g WHERE "
         + "(g.whiteUserId = :userId OR g.blackUserId = :userId) "
         + "AND g.status IN ('active','paused') "
         + "ORDER BY g.updatedAt DESC")
    List<GameEntity> findActiveGamesForUser(UUID userId);
}
