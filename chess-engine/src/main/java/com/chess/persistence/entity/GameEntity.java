package com.chess.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "games")
public class GameEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String mode;       // 'ai' | 'local' | 'online' | 'saved'

    @Column(nullable = false, length = 20)
    private String status = "waiting";

    @Column(name = "current_turn", nullable = false, length = 10)
    private String currentTurn = "white";

    // ── White player ──
    @Column(name = "white_user_id")
    private UUID whiteUserId;
    @Column(name = "white_username", nullable = false, length = 50)
    private String whiteUsername;
    @Column(name = "white_elo")
    private Integer whiteElo;
    @Column(name = "white_time_remaining_ms", nullable = false)
    private long whiteTimeRemainingMs;
    @Column(name = "white_is_ai", nullable = false)
    private boolean whiteIsAi = false;
    @Column(name = "white_ai_difficulty")
    private Integer whiteAiDifficulty;

    // ── Black player ──
    @Column(name = "black_user_id")
    private UUID blackUserId;
    @Column(name = "black_username", nullable = false, length = 50)
    private String blackUsername;
    @Column(name = "black_elo")
    private Integer blackElo;
    @Column(name = "black_time_remaining_ms", nullable = false)
    private long blackTimeRemainingMs;
    @Column(name = "black_is_ai", nullable = false)
    private boolean blackIsAi = false;
    @Column(name = "black_ai_difficulty")
    private Integer blackAiDifficulty;

    // ── Board ──
    @Column(name = "current_fen", nullable = false)
    private String currentFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Column(name = "half_move_clock", nullable = false)
    private int halfMoveClock = 0;

    @Column(name = "full_move_number", nullable = false)
    private int fullMoveNumber = 1;

    // ── Castling ──
    @Column(name = "white_kingside_castle",  nullable = false) private boolean whiteKingsideCastle  = true;
    @Column(name = "white_queenside_castle", nullable = false) private boolean whiteQueensideCastle = true;
    @Column(name = "black_kingside_castle",  nullable = false) private boolean blackKingsideCastle  = true;
    @Column(name = "black_queenside_castle", nullable = false) private boolean blackQueensideCastle = true;

    // ── En passant ──
    @Column(name = "en_passant_row") private Integer enPassantRow;
    @Column(name = "en_passant_col") private Integer enPassantCol;

    // ── Time control ──
    @Column(name = "time_control_type",         nullable = false, length = 20) private String timeControlType = "rapid";
    @Column(name = "time_control_initial_ms",   nullable = false) private long timeControlInitialMs  = 600_000;
    @Column(name = "time_control_increment_ms", nullable = false) private long timeControlIncrementMs = 0;

    // ── Result ──
    @Column(name = "result_winner",  length = 10) private String resultWinner;
    @Column(name = "result_reason",  length = 30) private String resultReason;

    @Column(length = 100)
    private String opening;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // ── Relations ──
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("moveNumber ASC")
    private List<GameMoveEntity> moves = new ArrayList<>();

    @PreUpdate void onUpdate() { this.updatedAt = Instant.now(); }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public UUID getId()                          { return id; }
    public String getMode()                      { return mode; }
    public void setMode(String v)                { this.mode = v; }
    public String getStatus()                    { return status; }
    public void setStatus(String v)              { this.status = v; }
    public String getCurrentTurn()               { return currentTurn; }
    public void setCurrentTurn(String v)         { this.currentTurn = v; }
    public UUID getWhiteUserId()                 { return whiteUserId; }
    public void setWhiteUserId(UUID v)           { this.whiteUserId = v; }
    public String getWhiteUsername()             { return whiteUsername; }
    public void setWhiteUsername(String v)       { this.whiteUsername = v; }
    public Integer getWhiteElo()                 { return whiteElo; }
    public void setWhiteElo(Integer v)           { this.whiteElo = v; }
    public long getWhiteTimeRemainingMs()        { return whiteTimeRemainingMs; }
    public void setWhiteTimeRemainingMs(long v)  { this.whiteTimeRemainingMs = v; }
    public boolean isWhiteIsAi()                 { return whiteIsAi; }
    public void setWhiteIsAi(boolean v)          { this.whiteIsAi = v; }
    public Integer getWhiteAiDifficulty()        { return whiteAiDifficulty; }
    public void setWhiteAiDifficulty(Integer v)  { this.whiteAiDifficulty = v; }
    public UUID getBlackUserId()                 { return blackUserId; }
    public void setBlackUserId(UUID v)           { this.blackUserId = v; }
    public String getBlackUsername()             { return blackUsername; }
    public void setBlackUsername(String v)       { this.blackUsername = v; }
    public Integer getBlackElo()                 { return blackElo; }
    public void setBlackElo(Integer v)           { this.blackElo = v; }
    public long getBlackTimeRemainingMs()        { return blackTimeRemainingMs; }
    public void setBlackTimeRemainingMs(long v)  { this.blackTimeRemainingMs = v; }
    public boolean isBlackIsAi()                 { return blackIsAi; }
    public void setBlackIsAi(boolean v)          { this.blackIsAi = v; }
    public Integer getBlackAiDifficulty()        { return blackAiDifficulty; }
    public void setBlackAiDifficulty(Integer v)  { this.blackAiDifficulty = v; }
    public String getCurrentFen()                { return currentFen; }
    public void setCurrentFen(String v)          { this.currentFen = v; }
    public int getHalfMoveClock()                { return halfMoveClock; }
    public void setHalfMoveClock(int v)          { this.halfMoveClock = v; }
    public int getFullMoveNumber()               { return fullMoveNumber; }
    public void setFullMoveNumber(int v)         { this.fullMoveNumber = v; }
    public boolean isWhiteKingsideCastle()       { return whiteKingsideCastle; }
    public void setWhiteKingsideCastle(boolean v){ this.whiteKingsideCastle = v; }
    public boolean isWhiteQueensideCastle()      { return whiteQueensideCastle; }
    public void setWhiteQueensideCastle(boolean v){ this.whiteQueensideCastle = v; }
    public boolean isBlackKingsideCastle()       { return blackKingsideCastle; }
    public void setBlackKingsideCastle(boolean v){ this.blackKingsideCastle = v; }
    public boolean isBlackQueensideCastle()      { return blackQueensideCastle; }
    public void setBlackQueensideCastle(boolean v){ this.blackQueensideCastle = v; }
    public Integer getEnPassantRow()             { return enPassantRow; }
    public void setEnPassantRow(Integer v)       { this.enPassantRow = v; }
    public Integer getEnPassantCol()             { return enPassantCol; }
    public void setEnPassantCol(Integer v)       { this.enPassantCol = v; }
    public String getTimeControlType()           { return timeControlType; }
    public void setTimeControlType(String v)     { this.timeControlType = v; }
    public long getTimeControlInitialMs()        { return timeControlInitialMs; }
    public void setTimeControlInitialMs(long v)  { this.timeControlInitialMs = v; }
    public long getTimeControlIncrementMs()      { return timeControlIncrementMs; }
    public void setTimeControlIncrementMs(long v){ this.timeControlIncrementMs = v; }
    public String getResultWinner()              { return resultWinner; }
    public void setResultWinner(String v)        { this.resultWinner = v; }
    public String getResultReason()              { return resultReason; }
    public void setResultReason(String v)        { this.resultReason = v; }
    public String getOpening()                   { return opening; }
    public void setOpening(String v)             { this.opening = v; }
    public Instant getCreatedAt()                { return createdAt; }
    public Instant getUpdatedAt()                { return updatedAt; }
    public List<GameMoveEntity> getMoves()       { return moves; }
}
