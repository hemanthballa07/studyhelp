package com.platform.qc.event;

import java.util.UUID;

/** Signals that an expert's rolling quality stats changed (placeholder for Slice 8 payments). */
public record ExpertQualityUpdated(UUID expertId, int reviewCount, int latestScore) {

    public static final String TYPE = "ExpertQualityUpdated";
}
