package com.platform.ai.domain;

import com.platform.shared.generation.CandidateAnswer;
import java.util.Optional;
import java.util.UUID;

public interface GenerationRepository {
    void save(UUID id, UUID questionId, CandidateAnswer answer);
    Optional<CandidateAnswer> findByQuestionId(UUID questionId);
}
