package com.chess.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long      accessTokenExpiryMs;
    private final long      refreshTokenExpiryMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms}") long accessExpiry,
            @Value("${jwt.refresh-token-expiry-ms}") long refreshExpiry) {
        this.key                 = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessExpiry;
        this.refreshTokenExpiryMs = refreshExpiry;
    }

    public String generateAccessToken(UUID userId, String username) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
            .signWith(key)
            .compact();
    }

    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiryMs))
            .signWith(key)
            .compact();
    }

    public Claims validateAndParse(String token) {
        return Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(validateAndParse(token).getSubject());
    }

    public long getAccessTokenExpiryMs()  { return accessTokenExpiryMs; }
    public long getRefreshTokenExpiryMs() { return refreshTokenExpiryMs; }
}
