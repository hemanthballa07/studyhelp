package com.platform.lifecycle.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Body of {@code POST /api/questions}. The student is derived from the authenticated principal (the
 * access token's {@code userId} claim, via {@code shared.security.CurrentUser}), never the request
 * body, so a caller can no longer attribute a question to another student.
 */
public record PostQuestionRequest(
        @NotBlank @Size(max = 120) String subject,
        @NotBlank @Size(max = 300) String title,
        @NotBlank @Size(max = 10_000) String body,
        @NotNull @Future Instant deadlineAt) {
}
