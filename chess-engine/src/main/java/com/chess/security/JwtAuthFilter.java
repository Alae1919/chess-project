package com.chess.security;

import com.chess.persistence.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService     jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService     = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }
        try {
            var claims = jwtService.validateAndParse(header.substring(7));
            // Refresh tokens share the signing key; they are only good for /api/auth/refresh
            boolean isRefreshToken = "refresh".equals(claims.get("type", String.class));
            if (!isRefreshToken && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Resolve by user id (sub), never by the username claim: usernames can
                // change, and the old lookup also matched emails, so a user named after
                // someone's email was authenticated as that person.
                userRepository.findById(UUID.fromString(claims.getSubject()))
                    .map(SecurityConfig::toUserDetails)
                    .ifPresent(userDetails -> SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities())));
            }
        } catch (Exception ignored) {
            // Invalid token — request proceeds unauthenticated
        }
        chain.doFilter(req, res);
    }
}
