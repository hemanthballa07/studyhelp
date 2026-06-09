package com.platform.ai.app;

public enum DecisionOutcome {
    /** Confidence >= tau_high: answer delivered without caveats. */
    PRODUCED,
    /** tau_low <= confidence < tau_high: answer delivered with a review flag. */
    FLAGGED,
    /** Confidence < tau_low: answer withheld; question escalated to human expert. */
    ABSTAINED
}
