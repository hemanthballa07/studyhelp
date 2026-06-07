package com.platform.expertportal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/claims}. The expert is taken from the principal, never the body. */
public record ClaimRequest(@NotBlank @Size(max = 120) String subject) {
}
