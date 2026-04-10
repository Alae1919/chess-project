package com.chess.api.controller;

import com.chess.api.dto.AuthDto;
import com.chess.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Register, login, token refresh")
public class AuthController {

    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user account")
    public AuthDto.AuthTokens register(@Valid @RequestBody AuthDto.RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public AuthDto.AuthTokens login(@Valid @RequestBody AuthDto.LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for new tokens")
    public AuthDto.AuthTokens refresh(@Valid @RequestBody AuthDto.RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }
}
