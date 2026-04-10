package com.chess.api.dto;

import jakarta.validation.constraints.*;

public final class AuthDto {

    public record LoginRequest(
        @NotBlank @Email  String email,
        @NotBlank @Size(min = 6) String password
    ) {}

    public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
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
