package com.platform.expertportal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/experts/subjects}: a subject the calling expert handles. */
public record SubjectRequest(@NotBlank @Size(max = 120) String subject) {
}
