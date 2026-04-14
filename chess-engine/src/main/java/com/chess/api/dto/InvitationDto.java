package com.chess.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class InvitationDto {

    private InvitationDto() {}

    public record SendInvitationRequest(
        @NotBlank String inviteeUsername,
        @NotBlank String timeControlType,
        @NotNull Long timeControlInitialMs,
        long timeControlIncrementMs
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
