package com.chess.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class UserDto {

    public record EloPoint(Instant date, int elo) {}

    public record UserStats(
        int gamesPlayed,
        int wins,
        int losses,
        int draws,
        double winRate,
        int currentStreak,
        int bestStreak,
        List<EloPoint> eloHistory
    ) {}

    public record Subscription(
        String plan,
        boolean active,
        Instant renewsAt
    ) {}

    public record Achievement(
        String  id,
        String  icon,
        String  name,
        String  description,
        boolean unlocked,
        Integer progress,
        Integer target
    ) {}

    public record UserPreferences(
        String  boardTheme,
        String  pieceStyle,
        String  language,
        boolean soundEnabled,
        boolean animationsEnabled,
        boolean notificationsEnabled,
        boolean confirmMoves,
        boolean showLegalMoves,
        boolean enableUndo,
        boolean realTimeAnalysis
    ) {}

    public record User(
        String          id,
        String          username,
        String          email,
        String          avatarUrl,
        String          country,
        int             elo,
        String          rank,
        Instant         memberSince,
        UserStats       stats,
        UserPreferences preferences,
        Subscription    subscription,
        List<Achievement> achievements
    ) {}

    // PATCH /me/preferences — all fields optional
    public record UpdatePreferencesRequest(
        String  boardTheme,
        String  pieceStyle,
        String  language,
        Boolean soundEnabled,
        Boolean animationsEnabled,
        Boolean notificationsEnabled,
        Boolean confirmMoves,
        Boolean showLegalMoves,
        Boolean enableUndo,
        Boolean realTimeAnalysis
    ) {}
}
