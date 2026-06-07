-- Expert Portal context (Slice 5). Expert portal owns these tables and never writes question state
-- (master-design 3): the atomic CLAIMABLE -> CLAIMED claim is performed by lifecycle through the
-- shared ClaimPort. The queue is a read-model projection of QuestionRouted / QuestionClaimed consumed
-- off the outbox; the claim itself reads the canonical questions table, never this projection.

CREATE TABLE claimable_questions (
    question_id UUID PRIMARY KEY,
    subject     TEXT NOT NULL,
    routed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_claimable_questions_subject ON claimable_questions (subject, routed_at);

-- The expert profile/subjects notion: which subjects an expert may claim from.
CREATE TABLE expert_subjects (
    expert_id UUID NOT NULL,
    subject   TEXT NOT NULL,
    PRIMARY KEY (expert_id, subject)
);

-- Append-only audit of every claim attempt (the persisted form of ExpertClaimAttempted).
CREATE TABLE claim_attempts (
    id           UUID PRIMARY KEY,
    expert_id    UUID NOT NULL,
    subject      TEXT NOT NULL,
    outcome      TEXT NOT NULL,
    question_id  UUID NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_claim_attempts_expert ON claim_attempts (expert_id, attempted_at);
