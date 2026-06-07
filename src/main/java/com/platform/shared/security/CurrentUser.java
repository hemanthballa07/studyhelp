package com.platform.shared.security;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the authenticated user's id from the access token's {@code userId} claim (minted by
 * identity). Lives in {@code shared} so every context derives the caller from the principal the same
 * way instead of trusting a request body. Fails loud (401) when the claim is absent or malformed, so
 * a misconfigured token can never silently attribute an action to a null or wrong user.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The caller's user id from the {@code userId} claim, or 401 if it is missing or not a UUID. */
    public static UUID id(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authenticated principal");
        }
        String userId = jwt.getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "access token is missing the userId claim");
        }
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "access token userId claim is not a UUID");
        }
    }
}
