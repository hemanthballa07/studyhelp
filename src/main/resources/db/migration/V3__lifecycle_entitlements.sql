-- Lifecycle-owned projection of identity's EntitlementChanged, consumed via the outbox + dispatcher.
-- Lifecycle is the only writer of its tables (master-design section 3); this read-model is consulted
-- during routing. It is updated idempotently: the dispatcher's processed_events guard plus the upsert
-- make a replayed EntitlementChanged a no-op. allowed_features holds the feature-name array as JSONB.
CREATE TABLE student_entitlements (
    user_id          UUID PRIMARY KEY,
    allowed_features JSONB NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
