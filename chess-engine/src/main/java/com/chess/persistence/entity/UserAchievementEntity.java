package com.chess.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_achievements")
@IdClass(UserAchievementId.class)
public class UserAchievementEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "achievement_id", length = 50)
    private String achievementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", insertable = false, updatable = false)
    private AchievementDefinitionEntity definition;

    @Column(nullable = false)
    private boolean unlocked = false;

    @Column(nullable = false)
    private int progress = 0;

    @Column(name = "unlocked_at")
    private Instant unlockedAt;

    public UUID getUserId()                          { return userId; }
    public void setUserId(UUID v)                    { this.userId = v; }
    public String getAchievementId()                 { return achievementId; }
    public void setAchievementId(String v)           { this.achievementId = v; }
    public AchievementDefinitionEntity getDefinition(){ return definition; }
    public boolean isUnlocked()                      { return unlocked; }
    public void setUnlocked(boolean v)               { this.unlocked = v; }
    public int getProgress()                         { return progress; }
    public void setProgress(int v)                   { this.progress = v; }
    public Instant getUnlockedAt()                   { return unlockedAt; }
    public void setUnlockedAt(Instant v)             { this.unlockedAt = v; }
}
