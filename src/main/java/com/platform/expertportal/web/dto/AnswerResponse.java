package com.platform.expertportal.web.dto;

import java.util.UUID;

/**
 * Body of {@code POST /api/answers}: the persisted answer. {@code stale} is true when the submit
 * arrived after the lease expired (or from a non-owner) and so was not delivered.
 */
public record AnswerResponse(UUID answerId, UUID questionId, boolean stale) {
}
