package com.chess.application;

import com.chess.api.dto.UserDto;
import com.chess.persistence.entity.*;
import com.chess.persistence.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository            userRepo;
    private final EloHistoryRepository      eloRepo;
    private final UserAchievementRepository achievementRepo;

    public UserService(UserRepository userRepo, EloHistoryRepository eloRepo,
                       UserAchievementRepository achievementRepo) {
        this.userRepo        = userRepo;
        this.eloRepo         = eloRepo;
        this.achievementRepo = achievementRepo;
    }

    public UserDto.User getFullProfile(UUID userId) {
        var user = userRepo.findByIdWithPreferences(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var achievements = achievementRepo.findByUserIdWithDefinition(userId);
        var eloHistory   = eloRepo.findByUserIdOrderByRecordedAtAsc(userId);
        return toUserDto(user, achievements, eloHistory);
    }

    public UserDto.UserStats getStats(UUID userId) {
        var user = userRepo.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var eloHistory = eloRepo.findByUserIdOrderByRecordedAtAsc(userId);
        return toStatsDto(user, eloHistory);
    }

    public List<UserDto.Achievement> getAchievements(UUID userId) {
        return achievementRepo.findByUserIdWithDefinition(userId)
            .stream().map(this::toAchievementDto).toList();
    }

    public List<UserDto.EloPoint> getEloHistory(UUID userId) {
        return eloRepo.findByUserIdOrderByRecordedAtAsc(userId)
            .stream()
            .map(e -> new UserDto.EloPoint(e.getRecordedAt(), e.getElo()))
            .toList();
    }

    @Transactional
    public UserDto.UserPreferences updatePreferences(UUID userId,
                                                      UserDto.UpdatePreferencesRequest req) {
        var user  = userRepo.findByIdWithPreferences(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var prefs = user.getPreferences();
        if (req.boardTheme()             != null) prefs.setBoardTheme(req.boardTheme());
        if (req.pieceStyle()             != null) prefs.setPieceStyle(req.pieceStyle());
        if (req.language()               != null) prefs.setLanguage(req.language());
        if (req.soundEnabled()           != null) prefs.setSoundEnabled(req.soundEnabled());
        if (req.animationsEnabled()      != null) prefs.setAnimationsEnabled(req.animationsEnabled());
        if (req.notificationsEnabled()   != null) prefs.setNotificationsEnabled(req.notificationsEnabled());
        if (req.confirmMoves()           != null) prefs.setConfirmMoves(req.confirmMoves());
        if (req.showLegalMoves()         != null) prefs.setShowLegalMoves(req.showLegalMoves());
        if (req.enableUndo()             != null) prefs.setEnableUndo(req.enableUndo());
        if (req.realTimeAnalysis()       != null) prefs.setRealTimeAnalysis(req.realTimeAnalysis());
        userRepo.save(user);
        return toPreferencesDto(prefs);
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private UserDto.User toUserDto(UserEntity u,
                                    List<UserAchievementEntity> ach,
                                    List<EloHistoryEntity> elo) {
        return new UserDto.User(
            u.getId().toString(),
            u.getUsername(),
            u.getEmail(),
            u.getAvatarUrl(),
            u.getCountry(),
            u.getElo(),
            u.getRank().getLabel(),
            u.getCreatedAt(),
            toStatsDto(u, elo),
            u.getPreferences() != null ? toPreferencesDto(u.getPreferences()) : null,
            new UserDto.Subscription(
                u.getSubscriptionPlan().getLabel(),
                u.isSubscriptionActive(),
                u.getSubscriptionRenewsAt()),
            ach.stream().map(this::toAchievementDto).toList()
        );
    }

    private UserDto.UserStats toStatsDto(UserEntity u, List<EloHistoryEntity> elo) {
        int games = u.getGamesPlayed();
        double winRate = games > 0 ? (double) u.getWins() / games * 100 : 0.0;
        return new UserDto.UserStats(
            games, u.getWins(), u.getLosses(), u.getDraws(),
            Math.round(winRate * 10.0) / 10.0,
            u.getCurrentStreak(), u.getBestStreak(),
            elo.stream().map(e -> new UserDto.EloPoint(e.getRecordedAt(), e.getElo())).toList()
        );
    }

    private UserDto.UserPreferences toPreferencesDto(UserPreferencesEntity p) {
        return new UserDto.UserPreferences(
            p.getBoardTheme(), p.getPieceStyle(), p.getLanguage(),
            p.isSoundEnabled(), p.isAnimationsEnabled(), p.isNotificationsEnabled(),
            p.isConfirmMoves(), p.isShowLegalMoves(), p.isEnableUndo(), p.isRealTimeAnalysis()
        );
    }

    private UserDto.Achievement toAchievementDto(UserAchievementEntity ua) {
        var def = ua.getDefinition();
        return new UserDto.Achievement(
            def.getId(), def.getIcon(), def.getName(), def.getDescription(),
            ua.isUnlocked(), ua.getProgress(), def.getTarget()
        );
    }

    // Add to UserService.java:
public UUID getUserIdByUsername(String username) {
    return userRepo.findByUsername(username)
        .map(u -> u.getId())
        .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
}

}
