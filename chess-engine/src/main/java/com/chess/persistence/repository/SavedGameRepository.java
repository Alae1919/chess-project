package com.chess.persistence.repository;

import com.chess.persistence.entity.SavedGameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SavedGameRepository extends JpaRepository<SavedGameEntity, UUID> {
    List<SavedGameEntity> findByUserIdOrderBySavedAtDesc(UUID userId);
}
