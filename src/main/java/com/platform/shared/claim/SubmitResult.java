package com.platform.shared.claim;

/**
 * The outcome of a conditional submit (master-design 6.3), augmented with the question's subject
 * so the expert portal can populate the AnswerSubmitted event without a cross-context query.
 * {@code subjectCode} is null when {@code outcome} is {@link SubmitOutcome#STALE} (the portal
 * emits no downstream event in that case, so the null is never forwarded).
 */
public record SubmitResult(SubmitOutcome outcome, String subjectCode) {
}
