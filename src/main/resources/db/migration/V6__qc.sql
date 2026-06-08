-- QC context: rubric review results. QC is the only writer of this table; lifecycle reads QC
-- outcomes via domain events (outbox). dimensions_json, violations_json, suggestions_json hold
-- structured scoring detail without requiring separate tables for Slice 7.
CREATE TABLE qc_reviews (
    id               UUID PRIMARY KEY,
    answer_id        UUID NOT NULL,
    question_id      UUID NOT NULL,
    expert_id        UUID NOT NULL,
    total_score      INT NOT NULL,
    status           TEXT NOT NULL,
    dimensions_json  JSONB NOT NULL DEFAULT '{}',
    violations_json  JSONB NOT NULL DEFAULT '[]',
    suggestions_json JSONB NOT NULL DEFAULT '[]',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_qc_reviews_answer ON qc_reviews (answer_id);
CREATE INDEX idx_qc_reviews_question ON qc_reviews (question_id);
