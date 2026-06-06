/**
 * Search context — Postgres full-text search (tsvector + GIN), then pgvector semantic recall
 * fused via reciprocal-rank. Drives duplicate detection before routing. Cross-context effects
 * flow via domain events (outbox + dispatcher).
 */
package com.platform.search;
