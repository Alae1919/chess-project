package com.chess.persistence.repository;

import com.chess.persistence.entity.UserAchievementEntity;
import com.chess.persistence.entity.UserAchievementId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface UserAchievementRepository
        extends JpaRepository<UserAchievementEntity, UserAchievementId> {

    @Query("SELECT ua FROM UserAchievementEntity ua "
         + "JOIN FETCH ua.definition "
         + "WHERE ua.userId = :userId")
    List<UserAchievementEntity> findByUserIdWithDefinition(UUID userId);
}
