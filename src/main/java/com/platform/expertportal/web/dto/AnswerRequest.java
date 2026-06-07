package com.platform.expertportal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body of {@code POST /api/answers}. The expert is taken from the principal, never the body. */
public record AnswerRequest(
        @NotNull UUID questionId,
        @NotBlank @Size(max = 50_000) String body) {
}
