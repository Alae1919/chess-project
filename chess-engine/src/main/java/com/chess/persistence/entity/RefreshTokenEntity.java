package com.chess.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public RefreshTokenEntity() {}

    public UUID getId()             { return id; }
    public UserEntity getUser()     { return user; }
    public void setUser(UserEntity v){ this.user = v; }
    public String getTokenHash()    { return tokenHash; }
    public void setTokenHash(String v){ this.tokenHash = v; }
    public Instant getExpiresAt()   { return expiresAt; }
    public void setExpiresAt(Instant v){ this.expiresAt = v; }
    public boolean isRevoked()      { return revoked; }
    public void setRevoked(boolean v){ this.revoked = v; }
    public Instant getCreatedAt()   { return createdAt; }
}
