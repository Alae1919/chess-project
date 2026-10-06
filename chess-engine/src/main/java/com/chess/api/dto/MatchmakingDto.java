package com.chess.api.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;

public final class MatchmakingDto {

    private MatchmakingDto() {}

    public record JoinQueueRequest(
        @NotBlank @Pattern(regexp = "blitz|rapid|classical|unlimited", message = "must be blitz, rapid, classical or unlimited") String timeControlType,
        @NotNull @PositiveOrZero @Max(86_400_000L) Long timeControlInitialMs,
        @PositiveOrZero @Max(3_600_000L) long timeControlIncrementMs
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
