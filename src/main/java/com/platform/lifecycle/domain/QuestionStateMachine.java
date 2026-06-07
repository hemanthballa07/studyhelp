package com.platform.lifecycle.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * The question lifecycle state machine (master-design section 6.1). Holds the one legal-transition
 * table the lifecycle context checks before every state change. Transitions are declared only here,
 * so the section 6.1 diagram and this table must stay in step. The machine is pure (no I/O); the
 * version-guarded conditional UPDATE that actually applies a transition lives in the repository.
 */
public class QuestionStateMachine {

    private static final Map<QuestionState, Set<QuestionState>> LEGAL = legalTransitions();

    /** Whether {@code from -> to} is one of the edges in section 6.1. */
    public boolean isLegal(QuestionState from, QuestionState to) {
        return LEGAL.getOrDefault(from, Set.of()).contains(to);
    }

    /** Guard form of {@link #isLegal}: rejects an illegal transition before any database write. */
    public void assertLegal(QuestionState from, QuestionState to) {
        if (!isLegal(from, to)) {
            throw new IllegalTransitionException(from, to);
        }
    }

    private static Map<QuestionState, Set<QuestionState>> legalTransitions() {
        EnumMap<QuestionState, Set<QuestionState>> table = new EnumMap<>(QuestionState.class);
        table.put(QuestionState.POSTED, Set.of(QuestionState.DEDUP_CHECKING));
        table.put(QuestionState.DEDUP_CHECKING, Set.of(QuestionState.ROUTED, QuestionState.DELIVERED));
        table.put(QuestionState.ROUTED, Set.of(QuestionState.CLAIMABLE));
        table.put(QuestionState.CLAIMABLE, Set.of(QuestionState.CLAIMED));
        table.put(QuestionState.CLAIMED, Set.of(QuestionState.IN_PROGRESS, QuestionState.CLAIM_EXPIRED));
        table.put(QuestionState.IN_PROGRESS, Set.of(QuestionState.SUBMITTED, QuestionState.CLAIM_EXPIRED));
        table.put(QuestionState.SUBMITTED, Set.of(QuestionState.IN_REVIEW));
        table.put(QuestionState.IN_REVIEW,
                Set.of(QuestionState.DELIVERED, QuestionState.REVISION_REQUESTED, QuestionState.REJECTED));
        table.put(QuestionState.REVISION_REQUESTED, Set.of(QuestionState.IN_PROGRESS));
        table.put(QuestionState.CLAIM_EXPIRED, Set.of(QuestionState.CLAIMABLE));
        table.put(QuestionState.DELIVERED, Set.of(QuestionState.RATED));
        return table;
    }
}
