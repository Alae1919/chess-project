package com.chess.persistence.repository;

import com.chess.persistence.entity.GameEntity;
import com.chess.persistence.entity.DatabaseEnums.GameStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GameRepository extends JpaRepository<GameEntity, UUID> {

    @Query("SELECT g FROM GameEntity g WHERE "
         + "(g.whiteUserId = :userId OR g.blackUserId = :userId) "
         + "AND g.status = :status "
         + "ORDER BY g.updatedAt DESC")
    Page<GameEntity> findMatchHistoryForUserWithStatus(
            @Param("userId") UUID userId,
            @Param("status") GameStatus status,
            Pageable pageable);

    default Page<GameEntity> findMatchHistoryForUser(UUID userId, Pageable pageable) {
        return findMatchHistoryForUserWithStatus(userId, GameStatus.finished, pageable);
    }

    @Query("SELECT g FROM GameEntity g WHERE "
         + "(g.whiteUserId = :userId OR g.blackUserId = :userId) "
         + "AND g.status IN :statuses "
         + "ORDER BY g.updatedAt DESC")
    List<GameEntity> findActiveGamesForUserWithStatuses(
            @Param("userId") UUID userId,
            @Param("statuses") List<GameStatus> statuses);

    @Query("SELECT g.id FROM GameEntity g WHERE g.mode = :mode AND g.status = :status")
    List<UUID> findIdsByModeAndStatus(@Param("mode") com.chess.persistence.entity.DatabaseEnums.GameMode mode,
                                      @Param("status") GameStatus status);

    /** Rows of (game id, number of moves played) for the given games, in one query. */
    @Query("SELECT m.game.id, COUNT(m) FROM GameMoveEntity m WHERE m.game.id IN :ids GROUP BY m.game.id")
    List<Object[]> countMovesByGameIds(@Param("ids") java.util.Collection<UUID> ids);

    default List<GameEntity> findActiveGamesForUser(UUID userId) {
        return findActiveGamesForUserWithStatuses(
                userId, List.of(GameStatus.active, GameStatus.paused));
    }
}
