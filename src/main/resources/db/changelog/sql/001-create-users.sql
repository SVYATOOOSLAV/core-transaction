CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    first_name      VARCHAR(100)    NOT NULL,
    last_name       VARCHAR(100)    NOT NULL,
    patronymic      VARCHAR(100),
    phone_number    VARCHAR(20)     NOT NULL UNIQUE,
    email           VARCHAR(150),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
