package com.chess.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false, length = 10)
    private String country = "FR";

    @Column(nullable = false)
    private int elo = 1200;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PlayerRank rank = PlayerRank.BRONZE;

    // ── Denormalised stats ──
    @Column(name = "games_played", nullable = false)
    private int gamesPlayed = 0;

    @Column(nullable = false)
    private int wins = 0;

    @Column(nullable = false)
    private int losses = 0;

    @Column(nullable = false)
    private int draws = 0;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak = 0;

    @Column(name = "best_streak", nullable = false)
    private int bestStreak = 0;

    // ── Subscription ──
    @Column(name = "subscription_plan", nullable = false)
    @Enumerated(EnumType.STRING)
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.FREE;

    @Column(name = "subscription_active", nullable = false)
    private boolean subscriptionActive = true;

    @Column(name = "subscription_renews_at")
    private Instant subscriptionRenewsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // ── Relations ──
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserPreferencesEntity preferences;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EloHistoryEntity> eloHistory = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserAchievementEntity> achievements = new ArrayList<>();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    // ── Enums ──
    public enum PlayerRank {
        BRONZE("Bronze"), ARGENT("Argent"), OR("Or"),
        PLATINE("Platine"), DIAMANT("Diamant"), GRAND_MAITRE("Grand Maître");
        private final String label;
        PlayerRank(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum SubscriptionPlan {
        FREE("Free"), SILVER("Silver"), GOLD("Gold"), PLATINE("Platine");
        private final String label;
        SubscriptionPlan(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ── Getters / Setters ──
    public UUID getId()                         { return id; }
    public String getUsername()                  { return username; }
    public void setUsername(String v)            { this.username = v; }
    public String getEmail()                     { return email; }
    public void setEmail(String v)               { this.email = v; }
    public String getPasswordHash()              { return passwordHash; }
    public void setPasswordHash(String v)        { this.passwordHash = v; }
    public String getAvatarUrl()                 { return avatarUrl; }
    public void setAvatarUrl(String v)           { this.avatarUrl = v; }
    public String getCountry()                   { return country; }
    public void setCountry(String v)             { this.country = v; }
    public int getElo()                          { return elo; }
    public void setElo(int v)                    { this.elo = v; }
    public PlayerRank getRank()                  { return rank; }
    public void setRank(PlayerRank v)            { this.rank = v; }
    public int getGamesPlayed()                  { return gamesPlayed; }
    public void setGamesPlayed(int v)            { this.gamesPlayed = v; }
    public int getWins()                         { return wins; }
    public void setWins(int v)                   { this.wins = v; }
    public int getLosses()                       { return losses; }
    public void setLosses(int v)                 { this.losses = v; }
    public int getDraws()                        { return draws; }
    public void setDraws(int v)                  { this.draws = v; }
    public int getCurrentStreak()                { return currentStreak; }
    public void setCurrentStreak(int v)          { this.currentStreak = v; }
    public int getBestStreak()                   { return bestStreak; }
    public void setBestStreak(int v)             { this.bestStreak = v; }
    public SubscriptionPlan getSubscriptionPlan(){ return subscriptionPlan; }
    public void setSubscriptionPlan(SubscriptionPlan v) { this.subscriptionPlan = v; }
    public boolean isSubscriptionActive()        { return subscriptionActive; }
    public void setSubscriptionActive(boolean v) { this.subscriptionActive = v; }
    public Instant getSubscriptionRenewsAt()     { return subscriptionRenewsAt; }
    public void setSubscriptionRenewsAt(Instant v){ this.subscriptionRenewsAt = v; }
    public Instant getCreatedAt()                { return createdAt; }
    public Instant getUpdatedAt()                { return updatedAt; }
    public UserPreferencesEntity getPreferences(){ return preferences; }
    public void setPreferences(UserPreferencesEntity v) { this.preferences = v; }
    public List<EloHistoryEntity> getEloHistory(){ return eloHistory; }
    public List<UserAchievementEntity> getAchievements() { return achievements; }
}
