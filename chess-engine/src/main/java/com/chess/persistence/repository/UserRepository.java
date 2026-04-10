package com.chess.persistence.repository;

import com.chess.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.preferences WHERE u.id = :id")
    Optional<UserEntity> findByIdWithPreferences(UUID id);
}
