package com.chess.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class MatchmakingDto {

    private MatchmakingDto() {}

    public record JoinQueueRequest(
        @NotBlank String timeControlType,
        @NotNull Long timeControlInitialMs,
        long timeControlIncrementMs
    ) {}

    public record QueueStatus(
        String queueEntryId,
        String status,
        Instant joinedAt,
        Integer estimatedWaitSeconds
    ) {}

    public record MatchFoundPayload(
        String gameId,
        String opponentUsername,
        Integer opponentElo,
        String playerColor,
        String timeControlType,
        long timeControlInitialMs,
        long timeControlIncrementMs
    ) {}
}
