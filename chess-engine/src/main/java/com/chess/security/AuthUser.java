package com.chess.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * The signed-in user as a request sees it: their name, and their id, which the login token
 * carries already. Controllers take the id from here rather than looking it up by name.
 */
public class AuthUser extends User {

    private final UUID id;

    public AuthUser(UUID id, String username) {
        // no password: a request is authenticated by its token, never by this object
        super(username, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.id = id;
    }

    public UUID id() { return id; }

    /**
     * The caller's id: from the principal when the token filter put an {@link AuthUser} there,
     * otherwise (a principal made some other way, as in tests) looked up by name.
     */
    public static UUID idOf(UserDetails principal, Function<String, UUID> byUsername) {
        return principal instanceof AuthUser user ? user.id() : byUsername.apply(principal.getUsername());
    }
}
