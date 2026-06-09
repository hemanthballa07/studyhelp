-- Persists AI-generated candidate answers (pre-verifier) with per-step citations.
-- UNIQUE(question_id) enforces one generation row per question at the DB level.
CREATE TABLE generations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL UNIQUE,
    steps       JSONB NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_generations_question_id ON generations(question_id);
