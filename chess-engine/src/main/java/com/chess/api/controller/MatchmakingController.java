package com.chess.api.controller;

import com.chess.api.dto.MatchmakingDto;
import com.chess.application.MatchmakingService;
import com.chess.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/matchmaking")
@Tag(name = "Matchmaking", description = "Random online matchmaking queue")
public class MatchmakingController {

    private final MatchmakingService matchmakingService;
    private final UserService        userService;

    public MatchmakingController(MatchmakingService matchmakingService, UserService userService) {
        this.matchmakingService = matchmakingService;
        this.userService        = userService;
    }

    @PostMapping("/queue")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Join the matchmaking queue")
    public MatchmakingDto.QueueStatus joinQueue(
            @Valid @RequestBody MatchmakingDto.JoinQueueRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        return matchmakingService.joinQueue(userId, req);
    }

    @DeleteMapping("/queue")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Leave the matchmaking queue")
    public void leaveQueue(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        matchmakingService.leaveQueue(userId);
    }

    @GetMapping("/queue/status")
    @Operation(summary = "Get current queue status (polling fallback)")
    public ResponseEntity<MatchmakingDto.QueueStatus> getQueueStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        return matchmakingService.getQueueStatus(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
