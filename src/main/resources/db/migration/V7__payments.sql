CREATE TABLE earnings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_event_id UUID UNIQUE NOT NULL,
    question_id     UUID NOT NULL,
    expert_id       UUID NOT NULL,
    amount_cents    INT  NOT NULL,
    status          TEXT NOT NULL DEFAULT 'ACCRUED',
    accrued_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
