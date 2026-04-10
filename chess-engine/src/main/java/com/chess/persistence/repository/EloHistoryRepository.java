package com.chess.persistence.repository;

import com.chess.persistence.entity.EloHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface EloHistoryRepository extends JpaRepository<EloHistoryEntity, Long> {
    List<EloHistoryEntity> findByUserIdOrderByRecordedAtAsc(UUID userId);
    List<EloHistoryEntity> findByUserIdOrderByRecordedAtDesc(UUID userId, Pageable pageable);
}
