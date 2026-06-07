package com.platform.lifecycle.domain;

import java.util.UUID;

/**
 * A lease the SLA sweep just expired (master-design 6.4): the question, the state it was in before
 * expiry (CLAIMED or IN_PROGRESS), its subject (so the queue projection can re-add it), the expert
 * whose lease lapsed, and the version after the expire transition.
 */
public record ExpiredLease(UUID questionId, String fromState, String subject, UUID claimedBy, long version) {
}
