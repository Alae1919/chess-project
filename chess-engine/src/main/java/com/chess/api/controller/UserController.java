package com.chess.api.controller;

import com.chess.api.dto.*;
import com.chess.application.*;
import com.chess.persistence.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User profile, stats, achievements, preferences")
public class UserController {

    private final UserService            userService;
    private final GamePersistenceService gamePersist;
    private final UserRepository         userRepository;

    public UserController(UserService userService, GamePersistenceService gamePersist,
                          UserRepository userRepository) {
        this.userService    = userService;
        this.gamePersist    = gamePersist;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the full profile of the authenticated user")
    public UserDto.User getMe(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.getFullProfile(extractId(userDetails));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update username, country, or avatar URL")
    public UserDto.User updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserDto.UpdateProfileRequest req) {
        return userService.updateProfile(extractId(userDetails), req);
    }

    @PatchMapping("/me/preferences")
    @Operation(summary = "Update user display and gameplay preferences")
    public UserDto.UserPreferences updatePreferences(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserDto.UpdatePreferencesRequest req) {
        return userService.updatePreferences(extractId(userDetails), req);
    }

    @GetMapping("/me/stats")
    @Operation(summary = "Get stats and ELO history")
    public UserDto.UserStats getStats(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.getStats(extractId(userDetails));
    }

    @GetMapping("/me/achievements")
    @Operation(summary = "Get all achievements for the current user")
    public List<UserDto.Achievement> getAchievements(
            @AuthenticationPrincipal UserDetails userDetails) {
        return userService.getAchievements(extractId(userDetails));
    }

    @GetMapping("/me/elo-history")
    @Operation(summary = "Get ELO history as a list of {date, elo} points")
    public List<UserDto.EloPoint> getEloHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        return userService.getEloHistory(extractId(userDetails));
    }

    @GetMapping("/me/match-history")
    @Operation(summary = "Paginated match history")
    public MatchHistoryDto.PagedMatchHistory getMatchHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // a page holds at most 50 games, whatever the caller asks for
        int pageSize = Math.max(1, Math.min(size, 50));
        int pageIndex = Math.max(0, page);
        var result = gamePersist.getMatchHistory(extractId(userDetails), pageIndex, pageSize);
        return new MatchHistoryDto.PagedMatchHistory(
            result.getContent(), pageIndex, pageSize,
            result.getTotalElements(), result.getTotalPages()
        );
    }

    @PostMapping("/me/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change the authenticated user's password")
    public void changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserDto.ChangePasswordRequest req) {
        userService.changePassword(extractId(userDetails), req);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete the authenticated user's account")
    public void deleteAccount(@AuthenticationPrincipal UserDetails userDetails) {
        userService.deleteAccount(extractId(userDetails));
    }

    // ── Saved games ──────────────────────────────────────────────────────────

    @GetMapping("/me/saved-games")
    @Operation(summary = "List saved games for the current user")
    public List<GameDto.SavedGame> getSavedGames(
            @AuthenticationPrincipal UserDetails userDetails) {
        return gamePersist.getSavedGames(extractId(userDetails));
    }

    @DeleteMapping("/me/saved-games/{savedGameId}")
    @Operation(summary = "Delete a saved game")
    public void deleteSavedGame(@PathVariable UUID savedGameId,
                                @AuthenticationPrincipal UserDetails userDetails) {
        gamePersist.deleteSavedGame(savedGameId, extractId(userDetails));
    }

    // ── User search ───────────────────────────────────────────────────────────

    @GetMapping("/search")
    @Operation(summary = "Search users by username prefix (for friend invites)")
    public List<UserDto.UserSummary> searchUsers(@RequestParam String username) {
        return userRepository.searchByUsernamePrefix(username).stream()
                .map(u -> new UserDto.UserSummary(
                        u.getId().toString(), u.getUsername(), u.getElo(), u.getAvatarUrl()))
                .toList();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private UUID extractId(UserDetails ud) {
        // Username stored as UUID string during registration — look it up
        return userService.getUserIdByUsername(ud.getUsername());
    }
}
