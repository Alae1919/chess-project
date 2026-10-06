package com.chess.api.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;

public final class InvitationDto {

    private InvitationDto() {}

    public record SendInvitationRequest(
        @NotBlank String inviteeUsername,
        @NotBlank @Pattern(regexp = "blitz|rapid|classical|unlimited", message = "must be blitz, rapid, classical or unlimited") String timeControlType,
        @NotNull @PositiveOrZero @Max(86_400_000L) Long timeControlInitialMs,
        @PositiveOrZero @Max(3_600_000L) long timeControlIncrementMs,
        /** Optional: the colour the inviter wants (a rematch swaps colours); random if omitted. */
        @Pattern(regexp = "white|black", message = "must be white or black") String inviterColor
    ) {}

    public record InvitationResponse(
        String invitationId,
        String inviterUsername,
        String inviteeUsername,
        String status,
        String timeControlType,
        long timeControlInitialMs,
        long timeControlIncrementMs,
        Instant createdAt,
        Instant expiresAt
    ) {}

    public record InviteReceivedPayload(
        String invitationId,
        String inviterUsername,
        String inviteeUsername,
        Integer inviterElo,
        String timeControlType,
        long timeControlInitialMs,
        long timeControlIncrementMs,
        String status,
        Instant createdAt,
        Instant expiresAt
    ) {}

    public record RespondRequest(
        @NotBlank String response
    ) {}
}
