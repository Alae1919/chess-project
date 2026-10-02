package com.chess.api.dto;

import jakarta.validation.constraints.*;

public final class AuthDto {

    /**
     * Letters, digits, '_' and '-'. Never '@': a username must not be mistakable
     * for an email address.
     */
    public static final String USERNAME_PATTERN = "^[A-Za-z0-9_-]{3,30}$";
    public static final String USERNAME_MESSAGE =
        "must be 3-30 characters: letters, digits, '_' or '-'";

    public record LoginRequest(
        @NotBlank @Email  String email,
        @NotBlank @Size(min = 6) String password
    ) {}

    public record RegisterRequest(
        @NotBlank @Pattern(regexp = USERNAME_PATTERN, message = USERNAME_MESSAGE) String username,
        @NotBlank @Email                   String email,
        @NotBlank @Size(min = 6, max = 100) String password
    ) {}

    public record AuthTokens(
        String accessToken,
        String refreshToken,
        long   expiresIn      // milliseconds
    ) {}

    public record RefreshRequest(
        @NotBlank String refreshToken
    ) {}
}
