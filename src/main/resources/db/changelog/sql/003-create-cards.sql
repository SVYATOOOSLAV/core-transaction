CREATE TABLE cards (
    id              BIGSERIAL       PRIMARY KEY,
    account_id      BIGINT          NOT NULL REFERENCES accounts(id),
    card_number     VARCHAR(19)     NOT NULL UNIQUE,
    expiry_date     DATE            NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
