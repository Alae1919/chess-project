package com.chess.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@DisplayName("JwtService — every token is its own")
class JwtServiceTest {

    private final JwtService jwt = new JwtService(
        "unit-test-secret-unit-test-secret-0123456789", 900_000, 604_800_000);

    @Test
    @DisplayName("two refresh tokens issued in the same second for one user are different")
    void refreshTokensDiffer() {
        UUID user = UUID.randomUUID();

        // JWT times have one-second resolution: without a unique id these two would be
        // byte for byte the same, and storing the second one (by its hash) fails
        assertNotEquals(jwt.generateRefreshToken(user), jwt.generateRefreshToken(user));
    }

    @Test
    @DisplayName("two access tokens issued in the same second for one user are different")
    void accessTokensDiffer() {
        UUID user = UUID.randomUUID();

        assertNotEquals(jwt.generateAccessToken(user, "ann"), jwt.generateAccessToken(user, "ann"));
    }
}
