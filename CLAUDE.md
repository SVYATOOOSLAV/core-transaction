# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Start the database (required before running the app)
docker-compose up -d

# Build
./gradlew build

# Run application (port 8090)
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "by.svyat.core.transaction.integration.TransactionIntegrationTest"

# Run integration tests only
./gradlew test --tests "by.svyat.core.transaction.integration.*"

# Clean build
./gradlew clean
```

## Architecture

Spring Boot 3.5 / Kotlin 1.9.25 microservice for banking transaction processing. Runs on port **8090**, backed by PostgreSQL 16 (port **5434** via Docker).

**Package**: `by.svyat.core.transaction`

**Layers** (strict separation):
- `api/controller/` — `*Api` interfaces (with Spring annotations) + `impl/` — REST controller implementations, base path `/api/v1/`
- `api/dto/` — `request/`, `response/`, and `outbox/` DTOs with Jakarta Bean Validation
- `service/` — interfaces + `impl/` — business logic, `@Transactional`
- `repository/` — Spring Data JPA repositories (including outbox repositories)
- `entity/` — JPA entities + `enums/` — enum types
- `mapping/` — entity↔DTO mappers
- `component/` — `AccountNumberGenerator` (uses JdbcTemplate for PostgreSQL sequences)
- `configuration/` — `OpenApiConfig`, `OutboxConfiguration`
- `outbox/` — Transactional Outbox pattern implementation (see below)
- `api/common/` — `GlobalExceptionHandler` (@RestControllerAdvice) + `BusinessException`

**Database migrations**: Liquibase with XML changelogs referencing SQL files under `src/main/resources/db/changelog/`. Hibernate DDL is set to `validate` — schema must exist before boot.

## Outbox Pattern

Implements a Transactional Outbox with Kafka-like partition semantics for reliable event publishing.

**Package structure** (`outbox/`):
- `OutboxProducer` / `impl/OutboxProducerImpl` — publishes events atomically within the business transaction
- `OutboxConsumer` / `impl/OutboxConsumerImpl` — polls partitions, routes events to handlers (implements `SmartLifecycle`)
- `OutboxEventHandler` interface + `handler/TransactionEventHandler` — processes events by aggregate type
- `configuration/OutboxProperties` — `@ConfigurationProperties(prefix = "outbox")`
- `component/OutboxCleanupTask` — scheduled daily at 3:00 AM, deletes old messages and expired locks
- `enums/OutboxAggregateType` — `TRANSACTION`, `ACCOUNT`
- `dto/OutboxEventMetadata` — metadata passed to handlers

**Database tables** (3 new):
- `outbox_messages` — LIST-partitioned by `partition_num` (8 partitions), stores JSONB payload
- `outbox_consumer_offsets` — tracks last processed offset per (consumer_group, partition)
- `outbox_partition_locks` — lease-based distributed locking per partition with TTL

**Key mechanics**:
- Partition key = account number → events for the same account are ordered within a partition
- `partition_num = abs(partitionKey.hashCode()) % partitionCount`
- Producer writes inside `@Transactional` → rollback eliminates both business change and event
- Consumer runs 1 thread per partition, acquires lease-based lock, processes in batches (default 50)
- Currently `TransactionServiceImpl` publishes `TRANSFER_COMPLETED` events for all transaction types

**Configuration** (application.yaml):
```yaml
outbox:
  partition-count: 8
  poll-interval-ms: 1000
  batch-size: 50
  consumer-group: core-transaction
  lock-ttl-seconds: 30
  cleanup-retention-days: 7
  producer-enabled: true
  consumer-enabled: true  # disabled in test profile
```

**Outbox event types** (enum `OutboxEventType`): `TRANSFER_COMPLETED`, `TRANSFER_FAILED`, `BALANCE_CHANGED`, `ACCOUNT_CREATED`, `ACCOUNT_DEACTIVATED`.

**Outbox payload DTO** (`TransactionOutboxPayload`): transactionId, type, sourceAccountNumber (nullable for credit-only), destinationAccountNumber, amount, currency, status.

## Key Design Decisions

**Account number generation**: Account numbers are auto-generated via PostgreSQL sequences with type-based prefixes: CHECKING→"1", SAVINGS→"2", DEPOSIT→"3", BROKERAGE→"4". Format: prefix + 19 zero-padded digits = 20 chars. `AccountNumberGenerator` component (in `component/` package) uses `JdbcTemplate` to call `nextval()` on per-type sequences (`seq_account_checking`, etc.).

**Account number as external identifier**: All API endpoints use `accountNumber` (String) instead of internal database `id` (Long) for account references. This applies to transfer requests, transaction responses, and account lookup (`GET /api/v1/accounts/{accountNumber}`).

**Idempotency**: All transaction write endpoints require a `idempotencyKey` (UUID). The `transactions` table has a unique constraint on this column.

**Pessimistic locking with deadlock prevention**: Both source and destination accounts are locked with `@Lock(LockModeType.PESSIMISTIC_WRITE)` via `findByAccountNumberForUpdate()`. Accounts are locked in consistent alphabetical order by `accountNumber` to prevent deadlocks. Credit-only operations (MoneyGift, Compensation) also lock the destination account.

**Transaction types**: `TRANSFER_SAVINGS`, `TRANSFER_DEPOSIT`, `TRANSFER_BROKERAGE` (internal), `INTERBANK_TRANSFER` (card-to-card), `SBP_TRANSFER` (phone-based P2P), `MONEY_GIFT`, `COMPENSATION` (credit-only — no debit), `CREDIT_PAYMENT` (debit-credit — debits source, credits destination).

**Metrics**: Business-level counters (`transactions.total` tagged by type/status) and account balance gauge (`accounts.balance`). HTTP-level timing is provided by Spring Boot Actuator (`http.server.requests`).

**Error handling**: Throw `BusinessException(httpStatus, message)` from service layer; `GlobalExceptionHandler` converts it to a structured `ErrorResponse` with timestamp and path.

**Account types**: `CHECKING`, `SAVINGS`, `DEPOSIT`, `BROKERAGE`. Balance column is `NUMERIC(19,4)` with a `CHECK >= 0` constraint.

**Entities**: `UserEntity`, `AccountEntity`, `CardEntity`, `TransactionEntity`, `OutboxMessageEntity`, `OutboxConsumerOffsetEntity`, `OutboxPartitionLockEntity`. Cards are linked to accounts (FK `account_id`), used for interbank transfers.

## Tech Stack

- Kotlin 1.9.25, Java 21, Spring Boot 3.5.12
- Spring Data JPA (Hibernate), Liquibase, PostgreSQL 16
- Jetty (not Tomcat)
- Micrometer + Prometheus for metrics
- kotlin-logging for structured logging
- Testing: JUnit 5, MockK, Testcontainers (PostgreSQL)

## Testing

**Test structure** (package `by.svyat.core.transaction`):
- `service/impl/` — unit tests with MockK for service layer
- `api/controller/` — `@WebMvcTest` controller tests
- `mapping/` — mapper unit tests
- `integration/` — full integration tests with Testcontainers PostgreSQL
- `outbox/` — unit tests for outbox producer

**Integration tests**:
- Base class: `IntegrationTestBase` — starts PostgreSQL container, configures `@SpringBootTest`, cleans DB via `@BeforeEach` in FK order (outbox messages → outbox offsets → outbox locks → transactions → cards → accounts → users)
- `TestDataFactory` — factory object with default-valued methods for all request DTOs
- `TestApiClient` — Spring `@Component` wrapping MockMvc for setup operations (create user/account/card, fund account). Returns `accountNumber` (String) from `createAccount()`.
- Tests use `@Nested` inner classes grouped by business operation (e.g., `TransferToSavings`, `SbpTransfer`, `Compensation`)
- Outbox consumer is disabled in test profile (`outbox.consumer-enabled: false`); producer writes are tested via `OutboxProducerIntegrationTest`

**Important**: DB cleanup order must respect FK constraints: outbox messages → outbox offsets → outbox locks → transactions → cards → accounts → users. `@DirtiesContext` does NOT clean the database — it only recreates Spring context.