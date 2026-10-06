package com.chess.persistence.entity;

import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matchmaking_queue")
public class MatchmakingQueueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "elo", nullable = false)
    private int elo;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_control_type", nullable = false, columnDefinition = "time_control_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TimeControlKind timeControlType;

    @Column(name = "time_control_initial_ms", nullable = false)
    private long timeControlInitialMs;

    @Column(name = "time_control_increment_ms", nullable = false)
    private long timeControlIncrementMs = 0;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "matched", nullable = false)
    private boolean matched = false;

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID getId()                            { return id; }
    public UUID getUserId()                        { return userId; }
    public void setUserId(UUID userId)             { this.userId = userId; }
    public String getUsername()                    { return username; }
    public void setUsername(String username)       { this.username = username; }
    public int getElo()                            { return elo; }
    public void setElo(int elo)                    { this.elo = elo; }
    public TimeControlKind getTimeControlType()    { return timeControlType; }
    public void setTimeControlType(TimeControlKind v) { this.timeControlType = v; }
    public long getTimeControlInitialMs()          { return timeControlInitialMs; }
    public void setTimeControlInitialMs(long v)    { this.timeControlInitialMs = v; }
    public long getTimeControlIncrementMs()        { return timeControlIncrementMs; }
    public void setTimeControlIncrementMs(long v)  { this.timeControlIncrementMs = v; }
    public Instant getJoinedAt()                   { return joinedAt; }
    public void setJoinedAt(Instant v)             { this.joinedAt = v; }
    public boolean isMatched()                     { return matched; }
    public void setMatched(boolean matched)        { this.matched = matched; }
}
