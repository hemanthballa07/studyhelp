package com.platform.shared.generation;

import java.util.List;

/** Port for the constrained answer-generation model. Implementations are profile-gated. */
public interface GenerationPort {
    CandidateAnswer generate(String question, List<ContextChunk> context);
}
