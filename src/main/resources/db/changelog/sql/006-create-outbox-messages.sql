CREATE TABLE outbox_messages (
    id              BIGSERIAL       NOT NULL,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(100)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    partition_key   VARCHAR(100)    NOT NULL,
    partition_num   INTEGER         NOT NULL,
    payload         JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, partition_num)
) PARTITION BY LIST (partition_num);

CREATE TABLE outbox_messages_p0 PARTITION OF outbox_messages FOR VALUES IN (0);
CREATE TABLE outbox_messages_p1 PARTITION OF outbox_messages FOR VALUES IN (1);
CREATE TABLE outbox_messages_p2 PARTITION OF outbox_messages FOR VALUES IN (2);
CREATE TABLE outbox_messages_p3 PARTITION OF outbox_messages FOR VALUES IN (3);
CREATE TABLE outbox_messages_p4 PARTITION OF outbox_messages FOR VALUES IN (4);
CREATE TABLE outbox_messages_p5 PARTITION OF outbox_messages FOR VALUES IN (5);
CREATE TABLE outbox_messages_p6 PARTITION OF outbox_messages FOR VALUES IN (6);
CREATE TABLE outbox_messages_p7 PARTITION OF outbox_messages FOR VALUES IN (7);

CREATE INDEX idx_outbox_partition_id ON outbox_messages (partition_num, id);
CREATE INDEX idx_outbox_created_at ON outbox_messages (created_at);