-- Expert portal owns answers (master-design 4/8). A submit that did not transition the question
-- (lease expired, wrong owner, wrong state -- master-design 6.3) is still persisted for audit but
-- flagged stale=true, and the expert portal emits nothing payout-triggering for it.
CREATE TABLE answers (
    id          UUID PRIMARY KEY,
    question_id UUID NOT NULL,
    expert_id   UUID NOT NULL,
    body        TEXT NOT NULL,
    stale       BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_answers_question ON answers (question_id, created_at);
