package com.chess.persistence.entity;

import com.chess.persistence.entity.DatabaseEnums.InvitationStatus;
import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_invitations")
public class GameInvitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "inviter_id", nullable = false)
    private UUID inviterId;

    @Column(name = "inviter_username", nullable = false, length = 50)
    private String inviterUsername;

    @Column(name = "invitee_id", nullable = false)
    private UUID inviteeId;

    @Column(name = "invitee_username", nullable = false, length = 50)
    private String inviteeUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_control_type", nullable = false, columnDefinition = "time_control_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TimeControlKind timeControlType;

    @Column(name = "time_control_initial_ms", nullable = false)
    private long timeControlInitialMs;

    @Column(name = "time_control_increment_ms", nullable = false)
    private long timeControlIncrementMs = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "invitation_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private InvitationStatus status = InvitationStatus.pending;

    @Column(name = "inviter_color", length = 5)
    private String inviterColor;

    @Column(name = "game_id")
    private UUID gameId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt = Instant.now().plusSeconds(120);

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID getId()                              { return id; }
    public UUID getInviterId()                       { return inviterId; }
    public void setInviterId(UUID inviterId)         { this.inviterId = inviterId; }
    public String getInviterUsername()               { return inviterUsername; }
    public void setInviterUsername(String v)         { this.inviterUsername = v; }
    public UUID getInviteeId()                       { return inviteeId; }
    public void setInviteeId(UUID inviteeId)         { this.inviteeId = inviteeId; }
    public String getInviteeUsername()               { return inviteeUsername; }
    public void setInviteeUsername(String v)         { this.inviteeUsername = v; }
    public TimeControlKind getTimeControlType()      { return timeControlType; }
    public void setTimeControlType(TimeControlKind v){ this.timeControlType = v; }
    public long getTimeControlInitialMs()            { return timeControlInitialMs; }
    public void setTimeControlInitialMs(long v)      { this.timeControlInitialMs = v; }
    public long getTimeControlIncrementMs()          { return timeControlIncrementMs; }
    public void setTimeControlIncrementMs(long v)    { this.timeControlIncrementMs = v; }
    public InvitationStatus getStatus()              { return status; }
    public void setStatus(InvitationStatus status)   { this.status = status; }
    /** "white", "black", or null when the colours are drawn at random. */
    public String getInviterColor()                  { return inviterColor; }
    public void setInviterColor(String v)            { this.inviterColor = v; }
    public UUID getGameId()                          { return gameId; }
    public void setGameId(UUID gameId)               { this.gameId = gameId; }
    public Instant getCreatedAt()                    { return createdAt; }
    public Instant getExpiresAt()                    { return expiresAt; }
    public void setExpiresAt(Instant expiresAt)      { this.expiresAt = expiresAt; }
}
