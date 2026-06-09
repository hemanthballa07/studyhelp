package com.platform.shared.generation;

import java.util.List;
import java.util.UUID;

/** One step of a generated answer, with the chunk IDs that support it. */
public record AnswerStep(String text, List<UUID> citationChunkIds) {}
