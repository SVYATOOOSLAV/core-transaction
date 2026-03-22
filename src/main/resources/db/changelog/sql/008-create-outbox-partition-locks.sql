CREATE TABLE outbox_partition_locks (
    id              BIGSERIAL       PRIMARY KEY,
    consumer_group  VARCHAR(100)    NOT NULL,
    partition_num   INTEGER         NOT NULL,
    locked_by       VARCHAR(200)    NOT NULL,
    locked_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ     NOT NULL,
    UNIQUE (consumer_group, partition_num)
);

CREATE INDEX idx_partition_locks_expires ON outbox_partition_locks (expires_at);
