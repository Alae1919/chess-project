package com.chess.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreferencesEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "board_theme", nullable = false, length = 20)
    private String boardTheme = "classic-wood";

    @Column(name = "piece_style", nullable = false, length = 20)
    private String pieceStyle = "standard";

    @Column(nullable = false, length = 10)
    private String language = "fr";

    @Column(name = "sound_enabled", nullable = false)
    private boolean soundEnabled = true;

    @Column(name = "animations_enabled", nullable = false)
    private boolean animationsEnabled = true;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    @Column(name = "confirm_moves", nullable = false)
    private boolean confirmMoves = false;

    @Column(name = "show_legal_moves", nullable = false)
    private boolean showLegalMoves = true;

    @Column(name = "enable_undo", nullable = false)
    private boolean enableUndo = false;

    @Column(name = "real_time_analysis", nullable = false)
    private boolean realTimeAnalysis = false;

    public UserPreferencesEntity() {}

    public static UserPreferencesEntity defaultsFor(UserEntity user) {
        var p = new UserPreferencesEntity();
        p.user = user;
        p.userId = user.getId();
        return p;
    }

    // Getters / setters
    public UUID getUserId()                      { return userId; }
    public String getBoardTheme()                { return boardTheme; }
    public void setBoardTheme(String v)          { this.boardTheme = v; }
    public String getPieceStyle()                { return pieceStyle; }
    public void setPieceStyle(String v)          { this.pieceStyle = v; }
    public String getLanguage()                  { return language; }
    public void setLanguage(String v)            { this.language = v; }
    public boolean isSoundEnabled()              { return soundEnabled; }
    public void setSoundEnabled(boolean v)       { this.soundEnabled = v; }
    public boolean isAnimationsEnabled()         { return animationsEnabled; }
    public void setAnimationsEnabled(boolean v)  { this.animationsEnabled = v; }
    public boolean isNotificationsEnabled()      { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean v){ this.notificationsEnabled = v; }
    public boolean isConfirmMoves()              { return confirmMoves; }
    public void setConfirmMoves(boolean v)       { this.confirmMoves = v; }
    public boolean isShowLegalMoves()            { return showLegalMoves; }
    public void setShowLegalMoves(boolean v)     { this.showLegalMoves = v; }
    public boolean isEnableUndo()                { return enableUndo; }
    public void setEnableUndo(boolean v)         { this.enableUndo = v; }
    public boolean isRealTimeAnalysis()          { return realTimeAnalysis; }
    public void setRealTimeAnalysis(boolean v)   { this.realTimeAnalysis = v; }
    public UserEntity getUser()                  { return user; }
    public void setUser(UserEntity v)            { this.user = v; }
}
