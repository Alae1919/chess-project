package com.chess.security;

import com.chess.persistence.entity.UserEntity;
import com.chess.persistence.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AuthUserCache: who a token is, for a minute")
class AuthUserCacheTest {

    private static UserEntity user(UUID id, String name) {
        var u = mock(UserEntity.class);
        when(u.getId()).thenReturn(id);
        when(u.getUsername()).thenReturn(name);
        return u;
    }

    @Test
    @DisplayName("reads a user once, until told the user changed")
    void readsOnceUntilEvicted() {
        UUID id = UUID.randomUUID();
        var users = mock(UserRepository.class);
        // made before the stubbing: a mock is not set up in the middle of setting up another
        var before = user(id, "alice");
        var after = user(id, "alicia");
        when(users.findById(id)).thenReturn(Optional.of(before), Optional.of(after));
        var registry = new SimpleMeterRegistry();
        var cache = new AuthUserCache(users, registry);

        assertEquals("alice", cache.get(id).orElseThrow().getUsername());
        assertEquals("alice", cache.get(id).orElseThrow().getUsername());
        verify(users, times(1)).findById(id);
        assertEquals(id, cache.get(id).orElseThrow().id());

        cache.evict(id);
        assertEquals("alicia", cache.get(id).orElseThrow().getUsername());
        verify(users, times(2)).findById(id);

        assertNotNull(registry.find("cache.gets").tag("cache", "auth-users").functionCounter(), registry.getMetersAsString());
    }

    @Test
    @DisplayName("remembers a missing user as missing")
    void missingUser() {
        UUID id = UUID.randomUUID();
        var users = mock(UserRepository.class);
        when(users.findById(id)).thenReturn(Optional.empty());
        var cache = new AuthUserCache(users, new SimpleMeterRegistry());
        assertTrue(cache.get(id).isEmpty());
        assertTrue(cache.get(id).isEmpty());
        verify(users, times(1)).findById(id);
    }
}
