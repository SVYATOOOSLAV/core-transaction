CREATE TABLE accounts (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    account_number  VARCHAR(20)     NOT NULL UNIQUE,
    account_type    VARCHAR(30)     NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'RUB',
    balance         NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

ALTER TABLE accounts ADD CONSTRAINT chk_account_type
    CHECK (account_type IN ('CHECKING', 'SAVINGS', 'DEPOSIT', 'BROKERAGE'));

ALTER TABLE accounts ADD CONSTRAINT chk_balance_non_negative
    CHECK (balance >= 0);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);
