package com.chess.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "elo_history")
public class EloHistoryEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private int elo;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    public EloHistoryEntity() {}
    public EloHistoryEntity(UserEntity user, int elo) {
        this.user = user;
        this.elo = elo;
    }

    public Long getId()          { return id; }
    public UserEntity getUser()  { return user; }
    public int getElo()          { return elo; }
    public Instant getRecordedAt(){ return recordedAt; }
}
