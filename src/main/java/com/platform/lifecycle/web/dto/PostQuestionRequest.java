package com.platform.lifecycle.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Body of {@code POST /api/questions}.
 *
 * <p>NOTE (Slice 4 security TODO): {@code studentId} is supplied by the client here. It must instead
 * be derived from the authenticated principal once the identity context exposes the caller's user id
 * (the JWT {@code sub} is currently the email, and the principal carries no user UUID). Until then a
 * caller could attribute a question to another student.
 */
public record PostQuestionRequest(
        @NotNull UUID studentId,
        @NotBlank @Size(max = 120) String subject,
        @NotBlank @Size(max = 300) String title,
        @NotBlank @Size(max = 10_000) String body,
        @NotNull @Future Instant deadlineAt) {
}
