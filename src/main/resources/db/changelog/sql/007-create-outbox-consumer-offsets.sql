CREATE TABLE outbox_consumer_offsets (
    id              BIGSERIAL       PRIMARY KEY,
    consumer_group  VARCHAR(100)    NOT NULL,
    partition_num   INTEGER         NOT NULL,
    last_offset     BIGINT          NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (consumer_group, partition_num)
);
