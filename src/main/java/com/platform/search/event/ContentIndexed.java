package com.platform.search.event;

import java.util.UUID;

public record ContentIndexed(UUID questionId) {
    public static final String TYPE = "ContentIndexed";
}
