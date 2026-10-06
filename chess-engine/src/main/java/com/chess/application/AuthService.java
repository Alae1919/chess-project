package com.chess.application;

import com.chess.api.dto.AuthDto;
import com.chess.persistence.entity.*;
import com.chess.persistence.repository.*;
import com.chess.security.JwtService;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository        userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final JwtService            jwt;
    private final PasswordEncoder       encoder;
    private final AuthenticationManager authManager;

    public AuthService(UserRepository userRepo, RefreshTokenRepository refreshRepo,
                       JwtService jwt, PasswordEncoder encoder,
                       AuthenticationManager authManager) {
        this.userRepo    = userRepo;
        this.refreshRepo = refreshRepo;
        this.jwt         = jwt;
        this.encoder     = encoder;
        this.authManager = authManager;
    }

    @Transactional
    public AuthDto.AuthTokens register(AuthDto.RegisterRequest req) {
        if (userRepo.existsByEmail(req.email()))
            throw new IllegalArgumentException("Email already in use");
        if (userRepo.existsByUsername(req.username()))
            throw new IllegalArgumentException("Username already taken");

        var user = new UserEntity();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(encoder.encode(req.password()));

        var prefs = UserPreferencesEntity.defaultsFor(user);
        user.setPreferences(prefs);
        userRepo.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthDto.AuthTokens login(AuthDto.LoginRequest req) {
        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (AuthenticationException ex) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        var user = userRepo.findByEmail(req.email()).orElseThrow();
        return issueTokens(user);
    }

    /**
     * Trades a refresh token for a new pair. Each token works once: a token that was already
     * traded in is a copy somebody kept, so showing it ends every session of that user (the
     * real owner signs in again; a thief's copy of the newest token dies with the rest).
     * {@code noRollbackFor}: that revocation must outlive the error reported to the caller.
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public AuthDto.AuthTokens refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        var stored  = refreshRepo.findByTokenHash(hash)
            .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (stored.isRevoked()) {
            refreshRepo.revokeAllForUser(stored.getUser().getId());
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }
        if (stored.getExpiresAt().isBefore(Instant.now()))
            throw new IllegalArgumentException("Refresh token expired or revoked");

        stored.setRevoked(true);
        var user = stored.getUser();
        return issueTokens(user);
    }

    /** Ends the session that owns this refresh token. A token we don't know is ignored. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshRepo.findByTokenHash(sha256(rawRefreshToken)).ifPresent(t -> t.setRevoked(true));
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private AuthDto.AuthTokens issueTokens(UserEntity user) {
        String accessToken  = jwt.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwt.generateRefreshToken(user.getId());

        var rt = new RefreshTokenEntity();
        rt.setUser(user);
        rt.setTokenHash(sha256(refreshToken));
        rt.setExpiresAt(Instant.now().plusMillis(jwt.getRefreshTokenExpiryMs()));
        refreshRepo.save(rt);

        return new AuthDto.AuthTokens(accessToken, refreshToken, jwt.getAccessTokenExpiryMs());
    }

    private String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
