CREATE TABLE corpus_index (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID UNIQUE NOT NULL,
    subject     TEXT,
    ts_content  TSVECTOR NOT NULL,
    indexed_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_corpus_index_fts ON corpus_index USING GIN (ts_content);
