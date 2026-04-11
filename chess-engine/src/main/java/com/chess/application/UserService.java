package com.chess.application;

import com.chess.api.dto.UserDto;
import com.chess.persistence.entity.*;
import com.chess.persistence.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository            userRepo;
    private final EloHistoryRepository      eloRepo;
    private final UserAchievementRepository achievementRepo;
    private final UserPreferencesMapper     preferencesMapper;
    private final UserMapper                userMapper;
    private final StatsMapper               statsMapper;
    private final PasswordEncoder           encoder;

    public UserService(UserRepository userRepo, EloHistoryRepository eloRepo,
                       UserAchievementRepository achievementRepo,
                       UserPreferencesMapper preferencesMapper,
                       UserMapper userMapper,
                       StatsMapper statsMapper,
                       PasswordEncoder encoder) {
        this.userRepo          = userRepo;
        this.eloRepo           = eloRepo;
        this.achievementRepo   = achievementRepo;
        this.preferencesMapper = preferencesMapper;
        this.userMapper        = userMapper;
        this.statsMapper       = statsMapper;
        this.encoder           = encoder;
    }

    public UserDto.User getFullProfile(UUID userId) {
        var user = userRepo.findByIdWithPreferences(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var achievements = achievementRepo.findByUserIdWithDefinition(userId);
        var eloHistory   = eloRepo.findByUserIdOrderByRecordedAtAsc(userId);
        return userMapper.toDto(user, achievements, eloHistory, statsMapper);
    }

    public UserDto.UserStats getStats(UUID userId) {
        var user = userRepo.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var eloHistory = eloRepo.findByUserIdOrderByRecordedAtAsc(userId);
        return statsMapper.toDto(user, eloHistory);
    }

    public List<UserDto.Achievement> getAchievements(UUID userId) {
        return achievementRepo.findByUserIdWithDefinition(userId)
            .stream().map(userMapper::toAchievementDto).toList();
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
        if (prefs == null) {
            prefs = UserPreferencesEntity.defaultsFor(user);
        }
        
        preferencesMapper.updateEntity(prefs, req);

        return preferencesMapper.toDto(prefs);
    }

    public UUID getUserIdByUsername(String username) {
        return userRepo.findByUsername(username)
            .map(UserEntity::getId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    }

    @Transactional
    public UserDto.User updateProfile(UUID userId, UserDto.UpdateProfileRequest req) {
        var user = userRepo.findByIdWithPreferences(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (req.username() != null) user.setUsername(req.username());
        if (req.country()  != null) user.setCountry(req.country());
        if (req.avatarUrl() != null) user.setAvatarUrl(req.avatarUrl());
        userRepo.save(user);
        return getFullProfile(userId);
    }

    @Transactional
    public void changePassword(UUID userId, UserDto.ChangePasswordRequest req) {
        var user = userRepo.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (!encoder.matches(req.oldPassword(), user.getPasswordHash()))
            throw new IllegalArgumentException("Incorrect current password");
        user.setPasswordHash(encoder.encode(req.newPassword()));
        userRepo.save(user);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        userRepo.deleteById(userId);
    }
}
