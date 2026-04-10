package com.chess.persistence.entity;

import com.chess.persistence.entity.DatabaseEnums.GameMode;
import com.chess.persistence.entity.DatabaseEnums.PlayerSide;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_games")
public class SavedGameEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "opponent_name", nullable = false, length = 50)
    private String opponentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "game_mode")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private GameMode mode;

    @Column(name = "turn_number", nullable = false)
    private int turnNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "player_color", nullable = false, columnDefinition = "piece_color")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private PlayerSide playerColor;

    @Column(length = 100)
    private String opening;

    @Column(name = "thumbnail_fen")
    private String thumbnailFen;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt = Instant.now();

    public UUID getId()              { return id; }
    public UUID getGameId()          { return gameId; }
    public void setGameId(UUID v)    { this.gameId = v; }
    public UUID getUserId()          { return userId; }
    public void setUserId(UUID v)    { this.userId = v; }
    public String getOpponentName()  { return opponentName; }
    public void setOpponentName(String v){ this.opponentName = v; }
    public GameMode getMode()      { return mode; }
    public void setMode(GameMode v){ this.mode = v; }
    public int getTurnNumber()       { return turnNumber; }
    public void setTurnNumber(int v) { this.turnNumber = v; }
    public PlayerSide getPlayerColor() { return playerColor; }
    public void setPlayerColor(PlayerSide v) { this.playerColor = v; }
    public String getOpening()       { return opening; }
    public void setOpening(String v) { this.opening = v; }
    public String getThumbnailFen()  { return thumbnailFen; }
    public void setThumbnailFen(String v){ this.thumbnailFen = v; }
    public Instant getSavedAt()      { return savedAt; }
}
