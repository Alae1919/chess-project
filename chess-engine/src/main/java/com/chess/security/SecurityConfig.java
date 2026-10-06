package com.chess.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.*;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import com.chess.persistence.entity.UserEntity;
import com.chess.persistence.repository.UserRepository;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserRepository userRepository;
    private final List<String> allowedOrigins;
    private final int authAttemptsPerMinute;

    public SecurityConfig(@Lazy JwtAuthFilter jwtAuthFilter, UserRepository userRepository,
                          @Value("${app.allowed-origins}") List<String> allowedOrigins,
                          @Value("${app.rate-limit.auth-per-minute:20}") int authAttemptsPerMinute) {
        this.jwtAuthFilter  = jwtAuthFilter;
        this.userRepository = userRepository;
        this.allowedOrigins = allowedOrigins;
        this.authAttemptsPerMinute = authAttemptsPerMinute;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/**",
                    "/error",   // the error page of a failed request must not itself need a login
                    "/ws/**",
                    // the container healthcheck; nginx only proxies /api and /ws, so it is not public
                    "/actuator/health/**",
                    "/swagger-ui/**", "/swagger-ui.html",
                    "/api-docs/**", "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            // No login is 401, so a client knows to sign in again; 403 would mean "signed in,
            // but not allowed", and a client has no reason to refresh its token for that
            .exceptionHandling(e -> e.authenticationEntryPoint(
                new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                    org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(new AuthRateLimitFilter(authAttemptsPerMinute), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // Password login only: the principal is the email. JwtAuthFilter resolves
        // tokens by user id and never comes through here.
        return email -> userRepository.findByEmail(email)
            .map(SecurityConfig::toUserDetails)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    /** The authenticated principal; controllers resolve the caller from its username. */
    static UserDetails toUserDetails(UserEntity u) {
        return User.withUsername(u.getUsername())
            .password(u.getPasswordHash())
            .roles("USER")
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        // Never "*": with credentials allowed, that would let any site call the API as the user
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*")); 
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}


// ─── APPLICATION SERVICES ────────────────────────────────────────────────────
