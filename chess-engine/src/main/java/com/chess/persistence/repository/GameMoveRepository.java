package com.chess.persistence.repository;

import com.chess.persistence.entity.GameMoveEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface GameMoveRepository extends JpaRepository<GameMoveEntity, Long> {

    /** The number of the game's last stored move (0 for none), read from the (game_id, move_number) index alone */
    @Query("select coalesce(max(m.moveNumber), 0) from GameMoveEntity m where m.game.id = :gameId")
    int lastMoveNumber(@Param("gameId") UUID gameId);
}
