package com.platform.lifecycle.app;

import com.platform.lifecycle.domain.QuestionRepository;
import com.platform.shared.claim.SubmitOutcome;
import com.platform.shared.claim.SubmitPort;
import com.platform.shared.claim.SubmitResult;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle's implementation of {@link SubmitPort}: the only writer of question state applies the
 * CLAIMED -> IN_PROGRESS start and the conditional IN_PROGRESS -> SUBMITTED submit (master-design 6.3),
 * each in one transaction with its append-only audit row. The submit emits no cross-context event
 * itself; the expert portal publishes {@code AnswerSubmitted} only when this returns
 * {@link SubmitOutcome#SUBMITTED}, so a stale submit can never trigger a downstream (payout) effect.
 * The returned {@link SubmitResult} carries the subject code so the portal can populate the event
 * without a cross-context query.
 */
@Service
public class LifecycleSubmitService implements SubmitPort {

    private static final String CLAIMED = "CLAIMED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String SUBMITTED = "SUBMITTED";

    private final QuestionRepository questions;

    public LifecycleSubmitService(QuestionRepository questions) {
        this.questions = questions;
    }

    @Override
    @Transactional
    public boolean start(UUID expertId, UUID questionId) {
        boolean started = questions.startWork(questionId, expertId);
        if (started) {
            questions.appendEvent(UUID.randomUUID(), questionId, "WorkStarted", CLAIMED, IN_PROGRESS, "{}");
        }
        return started;
    }

    @Override
    @Transactional
    public SubmitResult submit(UUID expertId, UUID questionId) {
        // fetch subject before the conditional UPDATE; subject never changes, so TOCTOU is not a concern
        String subject = questions.find(questionId).map(s -> s.subject()).orElse(null);
        if (!questions.submitIfOwned(questionId, expertId)) {
            return new SubmitResult(SubmitOutcome.STALE, null);
        }
        questions.appendEvent(UUID.randomUUID(), questionId, "QuestionSubmitted", IN_PROGRESS, SUBMITTED, "{}");
        return new SubmitResult(SubmitOutcome.SUBMITTED, subject);
    }
}
