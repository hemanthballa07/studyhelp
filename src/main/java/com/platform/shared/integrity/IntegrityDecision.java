package com.platform.shared.integrity;

/** The verdict returned by the integrity classifier for a given question prompt. */
public record IntegrityDecision(Mode mode, String promptSuffix) {

    public enum Mode { NORMAL, PEDAGOGICAL, REFUSE }
}
