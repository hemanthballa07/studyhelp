package com.platform.ai.domain;

import java.util.UUID;

/** An open-licensed study-material chunk stored in the AI retrieval corpus. */
public record AiCorpusChunk(UUID id, String source, String license, String chunkText) {}
