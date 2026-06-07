package com.platform.lifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The legal-transition table is the source of truth for question state changes (master-design
 * section 6.1). This test transcribes the table independently and pins it exactly: any pair not in
 * the table must be rejected, so a stray edge added to the engine fails here.
 */
class QuestionStateMachineTest {

    private final QuestionStateMachine machine = new QuestionStateMachine();

    private static final Map<QuestionState, Set<QuestionState>> LEGAL = Map.ofEntries(
            Map.entry(QuestionState.POSTED, Set.of(QuestionState.DEDUP_CHECKING)),
            Map.entry(QuestionState.DEDUP_CHECKING, Set.of(QuestionState.ROUTED, QuestionState.DELIVERED)),
            Map.entry(QuestionState.ROUTED, Set.of(QuestionState.CLAIMABLE)),
            Map.entry(QuestionState.CLAIMABLE, Set.of(QuestionState.CLAIMED)),
            Map.entry(QuestionState.CLAIMED, Set.of(QuestionState.IN_PROGRESS, QuestionState.CLAIM_EXPIRED)),
            Map.entry(QuestionState.IN_PROGRESS, Set.of(QuestionState.SUBMITTED, QuestionState.CLAIM_EXPIRED)),
            Map.entry(QuestionState.SUBMITTED, Set.of(QuestionState.IN_REVIEW)),
            Map.entry(QuestionState.IN_REVIEW,
                    Set.of(QuestionState.DELIVERED, QuestionState.REVISION_REQUESTED, QuestionState.REJECTED)),
            Map.entry(QuestionState.REVISION_REQUESTED, Set.of(QuestionState.IN_PROGRESS)),
            Map.entry(QuestionState.CLAIM_EXPIRED, Set.of(QuestionState.CLAIMABLE)),
            Map.entry(QuestionState.DELIVERED, Set.of(QuestionState.RATED)));

    @Test
    void legalTransitionsMatchTheStateMachineExactly() {
        for (QuestionState from : QuestionState.values()) {
            for (QuestionState to : QuestionState.values()) {
                boolean expected = LEGAL.getOrDefault(from, Set.of()).contains(to);
                assertThat(machine.isLegal(from, to))
                        .as("%s -> %s should be %s", from, to, expected ? "legal" : "illegal")
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void assertLegalThrowsForAnIllegalTransition() {
        assertThatThrownBy(() -> machine.assertLegal(QuestionState.POSTED, QuestionState.CLAIMABLE))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("POSTED")
                .hasMessageContaining("CLAIMABLE");
    }

    @Test
    void assertLegalAcceptsALegalTransition() {
        assertThatCode(() -> machine.assertLegal(QuestionState.POSTED, QuestionState.DEDUP_CHECKING))
                .doesNotThrowAnyException();
    }
}
