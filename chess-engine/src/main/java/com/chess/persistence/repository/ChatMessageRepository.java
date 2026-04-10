package com.chess.persistence.repository;

import com.chess.persistence.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findByGameIdOrderBySentAtAsc(UUID gameId);
}


// ─── DTOs (matching TypeScript interfaces exactly) ───────────────────────────
