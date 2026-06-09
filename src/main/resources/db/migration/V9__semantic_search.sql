CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE corpus_chunk (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL UNIQUE,
    chunk_text  TEXT NOT NULL,
    embedding   vector(384) NOT NULL,
    indexed_at  TIMESTAMPTZ DEFAULT now()
);

-- UNIQUE constraint above creates an implicit B-tree index on question_id (covers upsertChunk DELETE path).
CREATE INDEX idx_corpus_chunk_embedding ON corpus_chunk USING hnsw (embedding vector_cosine_ops);
