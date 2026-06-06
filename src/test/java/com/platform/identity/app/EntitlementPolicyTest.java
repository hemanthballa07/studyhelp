package com.platform.identity.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.identity.domain.Feature;
import com.platform.identity.domain.Plan;
import com.platform.identity.domain.Role;
import com.platform.identity.domain.SubscriptionStatus;
import org.junit.jupiter.api.Test;

class EntitlementPolicyTest {

    private final EntitlementPolicy policy = new EntitlementPolicy();

    @Test
    void studentCanPostQuestionEvenOnFree() {
        assertThat(policy.allows(Role.STUDENT, Plan.FREE, SubscriptionStatus.INACTIVE, Feature.POST_QUESTION))
                .isTrue();
    }

    @Test
    void studentCannotUseAiAnswerOnFree() {
        assertThat(policy.allows(Role.STUDENT, Plan.FREE, SubscriptionStatus.INACTIVE, Feature.AI_ANSWER))
                .isFalse();
    }

    @Test
    void studentCanUseAiAnswerOnActivePro() {
        assertThat(policy.allows(Role.STUDENT, Plan.PRO, SubscriptionStatus.ACTIVE, Feature.AI_ANSWER))
                .isTrue();
    }

    @Test
    void proButInactiveCannotUseAiAnswer() {
        assertThat(policy.allows(Role.STUDENT, Plan.PRO, SubscriptionStatus.INACTIVE, Feature.AI_ANSWER))
                .isFalse();
    }

    @Test
    void expertCanClaimQuestion() {
        assertThat(policy.allows(Role.EXPERT, Plan.FREE, SubscriptionStatus.INACTIVE, Feature.CLAIM_QUESTION))
                .isTrue();
    }

    @Test
    void studentCannotClaimQuestion() {
        assertThat(policy.allows(Role.STUDENT, Plan.FREE, SubscriptionStatus.INACTIVE, Feature.CLAIM_QUESTION))
                .isFalse();
    }

    @Test
    void adminHasAdminConsole() {
        assertThat(policy.allows(Role.ADMIN, Plan.FREE, SubscriptionStatus.INACTIVE, Feature.ADMIN_CONSOLE))
                .isTrue();
    }

    @Test
    void studentHasNoAdminConsole() {
        assertThat(policy.allows(Role.STUDENT, Plan.FREE, SubscriptionStatus.INACTIVE, Feature.ADMIN_CONSOLE))
                .isFalse();
    }
}
