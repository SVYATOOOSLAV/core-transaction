CREATE TABLE transactions (
    id                      BIGSERIAL       PRIMARY KEY,
    idempotency_key         UUID            NOT NULL UNIQUE,
    transaction_type        VARCHAR(30)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    source_account_id       BIGINT          REFERENCES accounts(id),
    destination_account_id  BIGINT          REFERENCES accounts(id),
    amount                  NUMERIC(19, 4)  NOT NULL,
    currency                VARCHAR(3)      NOT NULL DEFAULT 'RUB',
    description             VARCHAR(500),
    error_message           VARCHAR(500),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ
);

ALTER TABLE transactions ADD CONSTRAINT chk_transaction_type
    CHECK (transaction_type IN (
        'TRANSFER_SAVINGS', 'TRANSFER_DEPOSIT', 'TRANSFER_BROKERAGE',
        'INTERBANK_TRANSFER', 'MONEY_GIFT', 'COMPENSATION',
        'CREDIT_PAYMENT', 'SBP_TRANSFER'
    ));

ALTER TABLE transactions ADD CONSTRAINT chk_status
    CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED'));

ALTER TABLE transactions ADD CONSTRAINT chk_amount_positive
    CHECK (amount > 0);

CREATE INDEX idx_transactions_source_account ON transactions(source_account_id);
CREATE INDEX idx_transactions_dest_account ON transactions(destination_account_id);
CREATE INDEX idx_transactions_status ON transactions(status);
