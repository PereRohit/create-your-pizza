CREATE TABLE users (
    id              UUID PRIMARY KEY,
    role            TEXT NOT NULL,
    status          TEXT NOT NULL,
    username        TEXT UNIQUE,
    display_name    TEXT,
    password_hash   TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE trusted_client_credentials (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL UNIQUE REFERENCES users (id),
    api_key         TEXT UNIQUE,
    secret_hash     TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ
);

CREATE TABLE verification_keys (
    kid             TEXT PRIMARY KEY,
    alg             TEXT NOT NULL,
    public_jwk      JSONB NOT NULL,
    active          BOOLEAN NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);
