package com.platform.ai.domain;

import java.util.Optional;
import java.util.UUID;

public interface VerificationRepository {

    /** Returns 1 if a new row was inserted, 0 if the question was already verified (idempotent). */
    int save(VerificationResult result);

    Optional<VerificationResult> findByQuestionId(UUID questionId);
}
