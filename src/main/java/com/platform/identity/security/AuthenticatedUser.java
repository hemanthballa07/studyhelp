package com.platform.identity.security;

import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * A {@link org.springframework.security.core.userdetails.UserDetails} that also carries the platform
 * user id, so the JWT token customizer can mint a {@code userId} claim. Identity's principal therefore
 * carries the canonical user UUID, not just the email (which is the JWT {@code sub}); downstream
 * contexts derive the caller from that claim instead of trusting a request body.
 */
public class AuthenticatedUser extends User {

    private final UUID id;

    public AuthenticatedUser(UUID id, String email, String passwordHash,
            Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, authorities);
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
