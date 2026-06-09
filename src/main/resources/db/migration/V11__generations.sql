-- Persists AI-generated candidate answers (pre-verifier) with per-step citations.
CREATE TABLE generations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL,
    steps       JSONB NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_generations_question_id ON generations(question_id);
