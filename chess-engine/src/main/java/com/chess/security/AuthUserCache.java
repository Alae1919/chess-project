package com.chess.security;

import com.chess.persistence.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Who a token's user id is, for a minute. Every authenticated request used to load the user
 * from the database to check it still exists (and its name); a player's requests come seconds
 * apart, so this saves that query on almost all of them.
 *
 * Whatever changes the answer (a new username, a new password, a deleted account) calls
 * {@link #evict}, so the cache is never wrong for longer than it takes to make that change. A
 * user that does not exist is remembered as absent for the same minute.
 */
@Component
public class AuthUserCache {

    private final UserRepository users;
    private final Cache<UUID, Optional<AuthUser>> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(60))
        .maximumSize(10_000)
        .recordStats()
        .build();

    public AuthUserCache(UserRepository users, MeterRegistry registry) {
        this.users = users;
        CaffeineCacheMetrics.monitor(registry, cache, "auth-users");
    }

    public Optional<AuthUser> get(UUID id) {
        return cache.get(id, key -> users.findById(key).map(u -> new AuthUser(u.getId(), u.getUsername())));
    }

    /** Forget the user: their next request reads them from the database again. */
    public void evict(UUID id) {
        cache.invalidate(id);
    }
}
