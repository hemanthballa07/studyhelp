-- Persists 4-signal verifier results for AI-generated candidate answers (§10.3–10.4).
-- UNIQUE(question_id) enforces one verification per question at the DB level.
CREATE TABLE verifications (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id        UUID NOT NULL UNIQUE,
    groundedness_score DOUBLE PRECISION NOT NULL,
    structural_score   DOUBLE PRECISION NOT NULL,
    consistency_score  DOUBLE PRECISION NOT NULL,
    math_score         DOUBLE PRECISION NOT NULL,
    aggregate_score    DOUBLE PRECISION NOT NULL,
    created_at         TIMESTAMPTZ DEFAULT now()
);
