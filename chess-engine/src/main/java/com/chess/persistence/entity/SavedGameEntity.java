package com.chess.persistence.entity;

import jakarta.persistence.*;
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

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(name = "turn_number", nullable = false)
    private int turnNumber;

    @Column(name = "player_color", nullable = false, length = 10)
    private String playerColor;

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
    public String getMode()          { return mode; }
    public void setMode(String v)    { this.mode = v; }
    public int getTurnNumber()       { return turnNumber; }
    public void setTurnNumber(int v) { this.turnNumber = v; }
    public String getPlayerColor()   { return playerColor; }
    public void setPlayerColor(String v){ this.playerColor = v; }
    public String getOpening()       { return opening; }
    public void setOpening(String v) { this.opening = v; }
    public String getThumbnailFen()  { return thumbnailFen; }
    public void setThumbnailFen(String v){ this.thumbnailFen = v; }
    public Instant getSavedAt()      { return savedAt; }
}
