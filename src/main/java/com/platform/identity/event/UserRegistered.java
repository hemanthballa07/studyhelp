package com.platform.identity.event;

import java.util.UUID;

/** Domain event: a new user account was created. Published in-process (outbox arrives in Slice 3). */
public record UserRegistered(UUID userId, String email, String role) {
}
