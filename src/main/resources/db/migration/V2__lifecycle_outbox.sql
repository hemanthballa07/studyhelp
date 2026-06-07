-- Question lifecycle + transactional outbox. Lifecycle is the only writer of question state
-- (master-design section 3). question_events is an append-only audit log; the outbox guarantees
-- "state change <=> event" atomicity, and processed_events makes every consumer idempotent.

CREATE TYPE question_state AS ENUM (
    'POSTED', 'DEDUP_CHECKING', 'ROUTED', 'CLAIMABLE', 'CLAIMED',
    'IN_PROGRESS', 'SUBMITTED', 'IN_REVIEW', 'REVISION_REQUESTED',
    'DELIVERED', 'REJECTED', 'CLAIM_EXPIRED', 'RATED'
);

CREATE TABLE questions (
    id               UUID PRIMARY KEY,
    student_id       UUID NOT NULL,
    subject          TEXT NOT NULL,
    title            TEXT NOT NULL,
    body             TEXT NOT NULL,
    state            question_state NOT NULL,
    priority         INT NOT NULL DEFAULT 0,
    claimed_by       UUID NULL,
    claimed_at       TIMESTAMPTZ NULL,
    claim_expires_at TIMESTAMPTZ NULL,
    deadline_at      TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Partial index serving the claim sort (master-design section 8); only CLAIMABLE rows are indexed.
CREATE INDEX idx_questions_claimable
    ON questions (subject, priority DESC, created_at ASC)
    WHERE state = 'CLAIMABLE';

CREATE TABLE question_events (
    id          UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES questions(id),
    event_type  TEXT NOT NULL,
    from_state  question_state NULL,
    to_state    question_state NULL,
    payload     JSONB NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_question_events_question ON question_events (question_id, created_at);

-- Append-only guard: question_events is immutable history, so reject every UPDATE and DELETE.
CREATE FUNCTION reject_question_events_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'question_events is append-only (% rejected)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER question_events_append_only
    BEFORE UPDATE OR DELETE ON question_events
    FOR EACH ROW EXECUTE FUNCTION reject_question_events_mutation();

CREATE TABLE outbox (
    event_id       UUID PRIMARY KEY,
    aggregate_id   UUID NOT NULL,
    aggregate_type TEXT NOT NULL,
    event_type     TEXT NOT NULL,
    payload        JSONB NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ NULL
);

-- Serves the relay's "oldest unpublished first" poll.
CREATE INDEX idx_outbox_unpublished ON outbox (occurred_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    consumer     TEXT NOT NULL,
    event_id     UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
