package com.platform.expertportal.domain;

import java.time.Instant;
import java.util.UUID;

/** A row of the claimable-queue read view: a question available to claim for a subject. */
public record ClaimableQuestionView(UUID questionId, String subject, Instant routedAt) {
}
