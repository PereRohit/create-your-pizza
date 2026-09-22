CREATE TABLE products (
    id                   UUID PRIMARY KEY,
    name                 TEXT NOT NULL,
    product_type         TEXT NOT NULL,
    category             TEXT NOT NULL,
    price                NUMERIC(12, 2) NOT NULL,
    options_enabled      BOOLEAN,
    customisation_notes  TEXT,
    active               BOOLEAN NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);

CREATE TABLE combo_items (
    combo_id   UUID NOT NULL REFERENCES products (id),
    simple_id  UUID NOT NULL REFERENCES products (id),
    PRIMARY KEY (combo_id, simple_id)
);

CREATE TABLE option_entities (
    id          UUID PRIMARY KEY,
    kind        TEXT NOT NULL,
    name        TEXT NOT NULL,
    price       NUMERIC(12, 2) NOT NULL,
    is_base     BOOLEAN NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE menu_pdf (
    version       INTEGER PRIMARY KEY,
    pdf           BYTEA NOT NULL,
    generated_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE catalog_meta (
    id                       SMALLINT PRIMARY KEY,
    dirty                    BOOLEAN NOT NULL,
    last_catalog_change_at   TIMESTAMPTZ NOT NULL,
    last_pdf_version         INTEGER NOT NULL
);
