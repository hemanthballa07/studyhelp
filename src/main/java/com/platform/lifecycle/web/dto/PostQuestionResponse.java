package com.platform.lifecycle.web.dto;

import java.util.UUID;

/** Response of {@code POST /api/questions}: the id of the newly posted question. */
public record PostQuestionResponse(UUID id) {
}
