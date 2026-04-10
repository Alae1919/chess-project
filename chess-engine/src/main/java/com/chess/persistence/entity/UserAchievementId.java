package com.chess.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UserAchievementId implements Serializable {
    private UUID userId;
    private String achievementId;
    public UserAchievementId() {}
    public UserAchievementId(UUID userId, String achievementId) {
        this.userId = userId; this.achievementId = achievementId;
    }
    @Override public boolean equals(Object o) {
        if (!(o instanceof UserAchievementId u)) return false;
        return Objects.equals(userId, u.userId) && Objects.equals(achievementId, u.achievementId);
    }
    @Override public int hashCode() { return Objects.hash(userId, achievementId); }
}
