package com.platform.shared.generation;

import java.util.UUID;

/** A retrieved corpus chunk passed as evidence to the generation model. */
public record ContextChunk(UUID id, String text) {}
