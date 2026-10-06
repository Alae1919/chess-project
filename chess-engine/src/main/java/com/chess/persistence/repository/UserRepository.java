package com.chess.persistence.repository;

import com.chess.persistence.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    /**
     * The user, locked until the transaction ends: whoever else asks for the row waits. Used
     * when a result is added to a player's record, so two games ending together can't
     * each start from the same old numbers and overwrite one another.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.preferences WHERE u.id = :id")
    Optional<UserEntity> findByIdWithPreferences(UUID id);

    @Query("SELECT u FROM UserEntity u WHERE LOWER(u.username) LIKE LOWER(CONCAT(:prefix, '%')) ORDER BY u.username LIMIT 10")
    List<UserEntity> searchByUsernamePrefix(@Param("prefix") String prefix);
}
