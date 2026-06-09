-- AI corpus: open-licensed study material chunks with FTS + pgvector retrieval.
-- Distinct from corpus_index/corpus_chunk (owned by search) which index question content.
CREATE TABLE ai_corpus_chunk (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source      TEXT NOT NULL,
    license     TEXT NOT NULL,
    chunk_text  TEXT NOT NULL,
    ts_content  TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', chunk_text)) STORED,
    embedding   vector(384) NOT NULL,
    indexed_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_ai_corpus_chunk_ts        ON ai_corpus_chunk USING GIN(ts_content);
CREATE INDEX idx_ai_corpus_chunk_embedding ON ai_corpus_chunk USING hnsw (embedding vector_cosine_ops);

-- Records when a question was dispatched to the AI answer pipeline.
CREATE TABLE answer_requests (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ DEFAULT now()
);
