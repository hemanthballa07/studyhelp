-- Identity context schema. Identity is the only writer of these tables.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL,            -- STUDENT | EXPERT | ADMIN
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users(id),
    plan         TEXT NOT NULL,             -- FREE | PRO
    status       TEXT NOT NULL,             -- INACTIVE | ACTIVE
    activated_at TIMESTAMPTZ NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscriptions_user UNIQUE (user_id)
);
