# Диаграммы PlantUML

---

## 1. Концептуальная схема

```plantuml
@startuml

left to right direction
skinparam linetype ortho
skinparam shadowing false
skinparam nodesep 70
skinparam ranksep 70

skinparam entity {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
    FontSize 12
}
skinparam ArrowColor #000000
skinparam ArrowFontSize 10
skinparam ArrowFontColor #000000

hide circle

entity "Клиент" as client {
    идентификатор
    --
    имя
    фамилия
    отчество
    номер телефона
    электронная почта
}

entity "Счёт" as account {
    идентификатор
    --
    номер счёта
    тип счёта
    валюта
    баланс
    статус
}

entity "Карта" as card {
    идентификатор
    --
    номер карты
    срок действия
    статус
}

entity "Транзакция" as transaction {
    идентификатор
    --
    ключ идемпотентности
    тип операции
    статус
    сумма
    валюта
}

entity "Очередь\nдоставки" as outbox {
    идентификатор
    --
    тип агрегата
    тип события
    полезная нагрузка
    номер партиции
}

entity "Запись\nреестра" as ledger {
    идентификатор
    --
    ключ идемпотентности
    тип события
    тип операции
    счёт отправителя
    счёт получателя
    сумма
    статус
}

account -[hidden]down- card

client      ||--o{  account     : "открывает"
account     ||--o{  card        : "имеет"
account     |o--o{  transaction : "списание"
account     ||--o{  transaction : "зачисление"
transaction ||--||  outbox      : "порождает"
outbox      ||--||  ledger      : "доставляется в"

@enduml
```

---

## 2. Структурная схема ПО

```plantuml
@startuml
skinparam shadowing false
skinparam linetype ortho
skinparam nodesep 50
skinparam ranksep 40
skinparam package {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam ArrowColor #000000

package "core-transaction" {
    package "api" {
        package "controller" as ct_ctrl
        package "dto" as ct_dto
        package "common" as ct_common
    }
    package "service" as ct_svc
    package "repository" as ct_repo
    package "entity" as ct_entity
    package "mapping" as ct_map
    package "component" as ct_comp
    package "outbox" as ct_outbox
    package "configuration" as ct_cfg
}

package "core-ledger" {
    package "api" as lg_api {
        package "controller" as lg_ctrl
        package "dto" as lg_dto
        package "common" as lg_common
    }
    package "service" as lg_svc
    package "consumer" as lg_consumer
    package "repository" as lg_repo
    package "entity" as lg_entity
    package "mapping" as lg_map
    package "service/report" as lg_report
    package "configuration" as lg_cfg
}

ct_ctrl --> ct_svc
ct_ctrl --> ct_dto
ct_ctrl --> ct_common
ct_svc --> ct_repo
ct_svc --> ct_entity
ct_svc --> ct_map
ct_svc --> ct_comp
ct_svc --> ct_outbox
ct_repo --> ct_entity
ct_outbox --> ct_repo
ct_outbox --> ct_entity
ct_map --> ct_entity
ct_map --> ct_dto

lg_ctrl --> lg_svc
lg_ctrl --> lg_dto
lg_ctrl --> lg_common
lg_svc --> lg_repo
lg_svc --> lg_entity
lg_svc --> lg_map
lg_consumer --> lg_svc
lg_consumer --> lg_dto
lg_repo --> lg_entity
lg_map --> lg_entity
lg_map --> lg_dto
lg_report --> lg_repo
lg_report --> lg_entity
@enduml
```

---

## 3. Диаграмма компонентов

```plantuml
@startuml
skinparam shadowing false
skinparam linetype ortho
skinparam nodesep 60
skinparam ranksep 50
skinparam component {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam database {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam queue {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam ArrowColor #000000

package "core-transaction" {
    [REST API\n(UserController,\nAccountController,\nTransactionController)] as ct_api
    [Service Layer\n(UserService,\nAccountService,\nTransactionService)] as ct_svc
    [Repository Layer] as ct_repo
    [Outbox Producer] as ct_prod
    [Outbox Consumer] as ct_cons
}

package "core-ledger" {
    [Kafka Consumer\n(TransactionEventConsumer)] as lg_cons
    [Service Layer\n(TransactionLedgerService)] as lg_svc
    [Report Service\n(PDF / DOCX / HTML)] as lg_report
    [REST API\n(TransactionLedgerController,\nTransactionReportController)] as lg_api
    [Repository Layer] as lg_repo
}

database "transaction_db\n(PostgreSQL)" as tdb
database "ledger_db\n(PostgreSQL)" as ldb
queue "Kafka\ncore.transactions.request" as kafka

ct_api --> ct_svc
ct_svc --> ct_repo
ct_svc --> ct_prod
ct_prod --> ct_repo : "outbox_messages"
ct_repo --> tdb
ct_cons --> ct_repo : "poll outbox"
ct_cons --> kafka : "publish"

kafka --> lg_cons : "consume"
lg_cons --> lg_svc
lg_svc --> lg_repo
lg_repo --> ldb
lg_api --> lg_svc
lg_api --> lg_report
lg_report --> lg_repo
@enduml
```

---

## 4. Диаграмма коопераций

```plantuml
@startuml
skinparam shadowing false
skinparam object {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam ArrowColor #000000

object "Пользователь" as user
object "TransactionController" as ctrl
object "TransactionService" as svc
object "AccountRepository" as accRepo
object "TransactionRepository" as txRepo
object "OutboxProducer" as outbox
object "OutboxConsumer" as cons
object "Kafka" as kafka
object "TransactionEventConsumer" as lgCons
object "TransactionLedgerService" as lgSvc

user --> ctrl : "1: POST /api/v1/transactions/savings"
ctrl --> svc : "2: transferToSavings(request)"
svc --> accRepo : "3: findByAccountNumberForUpdate(source)"
svc --> accRepo : "4: findByAccountNumberForUpdate(dest)"
svc --> accRepo : "5: save(source), save(dest)"
svc --> txRepo : "6: save(transaction)"
svc --> outbox : "7: publish(event)"
outbox --> txRepo : "7.1: save(outboxMessage)"
ctrl --> user : "8: TransactionResponse"
cons --> outbox : "9: poll(partition)"
cons --> kafka : "10: send(kafkaMessage)"
kafka --> lgCons : "11: consume(record)"
lgCons --> lgSvc : "12: processEvent(message)"

@enduml
```

---

## 5. Диаграмма размещения

```plantuml
@startuml
skinparam shadowing false
skinparam node {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam component {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam database {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam ArrowColor #000000
skinparam nodesep 60
skinparam ranksep 50

node "Клиент" as client {
    [Браузер / HTTP-клиент]
}

node "Сервер core-transaction\n:8090" as srv_tx {
    [Spring Boot\n(Jetty)] as tx_app
    [Outbox Consumer\n(8 потоков)] as tx_outbox
}

node "Сервер core-ledger\n:8091" as srv_lg {
    [Spring Boot\n(Jetty)] as lg_app
    [Kafka Consumer\n(core-ledger-group)] as lg_kafka
}

node "Kafka-кластер (KRaft)" as kafka_cluster {
    [Брокер 1\n:9092] as k1
    [Брокер 2\n:9093] as k2
    [Брокер 3\n:9094] as k3
}

database "transaction_db\nPostgreSQL :5434" as tdb
database "ledger_db\nPostgreSQL :5435" as ldb

node "Мониторинг" as mon {
    [Kafka UI :8080]
    [Prometheus\n/actuator/prometheus]
}

client --> srv_tx : "HTTP REST"
client --> srv_lg : "HTTP REST"
tx_app --> tdb : "JDBC"
tx_outbox --> tdb : "JDBC (poll)"
tx_outbox --> kafka_cluster : "Kafka Producer"
kafka_cluster --> lg_kafka : "Kafka Consumer"
lg_app --> ldb : "JDBC"
lg_kafka --> ldb : "JDBC"
srv_tx --> mon : "metrics"
srv_lg --> mon : "metrics"
@enduml
```

---

## 6. Диаграмма классов core-transaction

```plantuml
@startuml
skinparam shadowing false
skinparam class {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam ArrowColor #000000
skinparam nodesep 40
skinparam ranksep 30

title core-transaction

interface TransactionApi {
    + transferToSavings(request): ResponseEntity
    + transferToDeposit(request): ResponseEntity
    + transferToBrokerage(request): ResponseEntity
    + interbankTransfer(request): ResponseEntity
    + sbpTransfer(request): ResponseEntity
    + processMoneyGift(request): ResponseEntity
    + processCompensation(request): ResponseEntity
    + processCreditPayment(request): ResponseEntity
    + getTransaction(id): ResponseEntity
    + getTransactionsByAccount(accountNumber): ResponseEntity
}

class TransactionController implements TransactionApi

interface TransactionService {
    + transferToSavings(request): TransactionResponse
    + transferToDeposit(request): TransactionResponse
    + transferToBrokerage(request): TransactionResponse
    + interbankTransfer(request): TransactionResponse
    + sbpTransfer(request): TransactionResponse
    + processMoneyGift(request): TransactionResponse
    + processCompensation(request): TransactionResponse
    + processCreditPayment(request): TransactionResponse
}

class TransactionServiceImpl implements TransactionService

interface AccountApi {
    + createAccount(request): ResponseEntity
    + getAccount(accountNumber): ResponseEntity
    + getAccountsByUser(userId): ResponseEntity
}

class AccountController implements AccountApi

interface AccountService {
    + createAccount(userId, type, currency): AccountResponse
    + getAccount(accountNumber): AccountResponse
    + getAccountsByUser(userId): List
}

class AccountServiceImpl implements AccountService

interface UserApi {
    + createUser(request): ResponseEntity
    + getUser(id): ResponseEntity
    + getUserByPhone(phoneNumber): ResponseEntity
}

class UserController implements UserApi

interface UserService {
    + createUser(...): UserResponse
    + getUser(id): UserResponse
    + getUserByPhone(phone): UserResponse
}

class UserServiceImpl implements UserService

interface OutboxProducer {
    + publish(aggregateType, aggregateId, eventType, partitionKey, payload)
}

class OutboxProducerImpl implements OutboxProducer

interface OutboxConsumer {
    + start()
    + stop()
    + isRunning(): Boolean
}

class OutboxConsumerImpl implements OutboxConsumer

class AccountNumberGenerator {
    + generate(accountType): String
}

TransactionController --> TransactionService
AccountController --> AccountService
UserController --> UserService
TransactionServiceImpl --> AccountRepository
TransactionServiceImpl --> TransactionRepository
TransactionServiceImpl --> OutboxProducer
AccountServiceImpl --> AccountRepository
AccountServiceImpl --> AccountNumberGenerator
UserServiceImpl --> UserRepository

@enduml
```

---

## 7. Диаграмма классов core-ledger

```plantuml
@startuml
skinparam shadowing false
skinparam class {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam ArrowColor #000000
skinparam nodesep 40
skinparam ranksep 30

title core-ledger

interface TransactionLedgerApi {
    + getTransaction(transactionId): ResponseEntity
    + getTransactionsByAccount(accountNumber): ResponseEntity
    + getAccountHistory(accountNumber, filters, pageable): ResponseEntity
    + getLatestTransactions(limit): ResponseEntity
}

class TransactionLedgerController implements TransactionLedgerApi

interface TransactionReportApi {
    + getAccountReport(accountNumber, format, filters): ResponseEntity
}

class TransactionReportController implements TransactionReportApi

interface TransactionLedgerService {
    + processEvent(message): Boolean
    + getByTransactionId(transactionId): TransactionLedgerEntity
    + getByAccountNumber(accountNumber): List
    + getAccountHistory(accountNumber, filters, pageable): Page
    + getLatest(limit): List
}

class TransactionLedgerServiceImpl implements TransactionLedgerService

interface TransactionReportService {
    + generateReport(accountNumber, format, filters): ByteArray
    + getContentType(format): String
    + getFileExtension(format): String
}

class TransactionReportServiceImpl implements TransactionReportService

interface ReportGeneratorStrategy {
    + generate(reportData): ByteArray
    + supportedFormat(): ReportFormat
}

class PdfReportGenerator implements ReportGeneratorStrategy
class DocxReportGenerator implements ReportGeneratorStrategy
class HtmlReportGenerator implements ReportGeneratorStrategy

class TransactionEventConsumer {
    + consume(record, acknowledgment)
}

TransactionLedgerController --> TransactionLedgerService
TransactionReportController --> TransactionReportService
TransactionEventConsumer --> TransactionLedgerService
TransactionReportServiceImpl --> ReportGeneratorStrategy
TransactionLedgerServiceImpl --> TransactionLedgerRepository
@enduml
```

---

## 8. Диаграмма последовательности

```plantuml
@startuml
skinparam shadowing false
skinparam sequence {
    ParticipantBackgroundColor #FFFFFF
    ParticipantBorderColor #000000
    ParticipantFontColor #000000
    LifeLineBorderColor #000000
    ArrowColor #000000
    ArrowFontColor #000000
}
skinparam nodesep 20

actor "Пользователь" as user
participant "TransactionController" as ctrl
participant "TransactionService" as svc
participant "AccountRepository" as accRepo
participant "TransactionRepository" as txRepo
participant "OutboxProducer" as outbox
database "transaction_db" as db
queue "Kafka" as kafka
participant "TransactionEventConsumer" as lgCons
participant "TransactionLedgerService" as lgSvc
database "ledger_db" as ldb

user -> ctrl : POST /api/v1/transactions/savings\n{idempotencyKey, source, dest, amount}
activate ctrl
ctrl -> svc : transferToSavings(request)
activate svc

svc -> accRepo : findByAccountNumberForUpdate(source)
activate accRepo
accRepo -> db : SELECT ... FOR UPDATE
accRepo --> svc : sourceAccount
deactivate accRepo

svc -> accRepo : findByAccountNumberForUpdate(dest)
activate accRepo
accRepo -> db : SELECT ... FOR UPDATE
accRepo --> svc : destAccount
deactivate accRepo

svc -> svc : Проверка баланса\nи типа счёта

svc -> accRepo : save(source: balance -= amount)
svc -> accRepo : save(dest: balance += amount)

svc -> txRepo : save(transaction: COMPLETED)
activate txRepo
txRepo --> svc : transaction
deactivate txRepo

svc -> outbox : publish(TRANSACTION,\naggregateId, TRANSFER_COMPLETED,\npartitionKey, payload)
activate outbox
outbox -> db : INSERT INTO outbox_messages
outbox --> svc
deactivate outbox

svc --> ctrl : TransactionResponse
deactivate svc
ctrl --> user : 200 OK + TransactionResponse
deactivate ctrl

... Outbox Consumer (асинхронно) ...

kafka <- db : OutboxConsumer\nчитает и публикует
kafka -> lgCons : consume(record)
activate lgCons
lgCons -> lgSvc : processEvent(message)
activate lgSvc
lgSvc -> ldb : existsByIdempotencyKey(key)
lgSvc -> ldb : INSERT INTO transaction_ledger
lgSvc --> lgCons : true
deactivate lgSvc
lgCons -> kafka : acknowledge()
deactivate lgCons
@enduml
```

---

## 9. Диаграмма прецедентов

```plantuml
@startuml
skinparam shadowing false
skinparam usecase {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam actor {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam ArrowColor #000000
skinparam rectangle {
    BackgroundColor #FFFFFF
    BorderColor #000000
}

left to right direction

actor "Пользователь" as user
actor "Outbox Consumer" as outbox
actor "Kafka Consumer" as kafkaCons

rectangle "core-transaction" {
    usecase "Создать клиента" as UC1
    usecase "Открыть счёт" as UC2
    usecase "Перевод между счетами" as UC3
    usecase "Перевод через СБП" as UC4
    usecase "Межбанковский перевод" as UC5
    usecase "Денежный подарок" as UC6
    usecase "Компенсация" as UC7
    usecase "Погашение кредита" as UC8
    usecase "Проверка баланса" as UC_BAL
    usecase "Блокировка счетов" as UC_LOCK
    usecase "Сохранение события\nв Outbox" as UC_OUT
    usecase "Публикация\nв Kafka" as UC_PUB
}

rectangle "core-ledger" {
    usecase "Просмотр истории\nтранзакций" as UC9
    usecase "Фильтрация по\nдате / типу / статусу" as UC_FILT
    usecase "Формирование\nотчёта" as UC10
    usecase "Выбор формата\n(PDF / DOCX / HTML)" as UC_FMT
    usecase "Сохранение записи\nв реестр" as UC_SAVE
}

user --> UC1
user --> UC2
user --> UC3
user --> UC4
user --> UC5
user --> UC6
user --> UC7
user --> UC8
user --> UC9
user --> UC10

UC3 ..> UC_BAL : <<include>>
UC4 ..> UC_BAL : <<include>>
UC5 ..> UC_BAL : <<include>>
UC8 ..> UC_BAL : <<include>>

UC3 ..> UC_LOCK : <<include>>
UC3 ..> UC_OUT : <<include>>
UC4 ..> UC_OUT : <<include>>
UC5 ..> UC_OUT : <<include>>
UC6 ..> UC_OUT : <<include>>
UC7 ..> UC_OUT : <<include>>
UC8 ..> UC_OUT : <<include>>

UC9 ..> UC_FILT : <<extend>>
UC10 ..> UC_FMT : <<extend>>

outbox --> UC_PUB
UC_PUB ..> UC_OUT : <<include>>

kafkaCons --> UC_SAVE
@enduml
```

---

## 10. Функциональная модель проведения транзакций

```plantuml
@startuml
skinparam shadowing false
skinparam ActivityBackgroundColor #FFFFFF
skinparam ActivityBorderColor #000000
skinparam ActivityFontColor #000000
skinparam ArrowColor #000000
skinparam DiamondBackgroundColor #FFFFFF
skinparam DiamondBorderColor #000000

title Функциональная модель: проведение транзакции

start

:Пользователь отправляет запрос;

if (Данные корректны?) then (да)
else (нет)
    :Ошибка валидации (400);
    stop
endif

if (idempotencyKey уникален?) then (да)
else (нет)
    :Ошибка (409 Conflict);
    stop
endif

if (Счета существуют и активны?) then (да)
else (нет)
    :Бизнес-ошибка (404);
    stop
endif

:Блокировка счетов (алфавитный порядок);

if (Операция с дебетом?) then (да)
    if (Баланс >= сумма?) then (да)
        :Списание со счёта-источника;
    else (нет)
        :Транзакция FAILED;
        stop
    endif
else (нет)
endif

:Зачисление на счёт-получатель;
:Транзакция COMPLETED + событие в Outbox;

fork
    :Ответ пользователю;
fork again
    :Outbox Consumer читает событие;
    :Публикация в Kafka;
    :Kafka Consumer получает событие;
    if (Дубликат?) then (да)
        :Пропустить;
    else (нет)
        :Сохранить в реестр;
    endif
    :Подтвердить (ack);
end fork

stop
@enduml
```

---

## 11. Расширенная диаграмма состояний

```plantuml
@startuml
skinparam shadowing false
skinparam state {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam ArrowColor #000000
skinparam nodesep 30
skinparam ranksep 30

title Жизненный цикл транзакции, Outbox-события и записи реестра

state "Транзакция\n(core-transaction)" as tx {
    [*] --> PENDING : Создание\nтранзакции

    PENDING --> COMPLETED : Успешное списание\nи зачисление
    PENDING --> FAILED : Недостаточно средств /\nсчёт неактивен /\nошибка
    PENDING --> CANCELLED : Системная отмена

    COMPLETED --> [*]
    FAILED --> [*]
    CANCELLED --> [*]

    state PENDING : Транзакция создана,\nожидает выполнения.
    state COMPLETED : Средства переведены.\nСобытие создано в Outbox.
    state FAILED : Балансы не изменены.\nОшибка зафиксирована\nв error_message.
    state CANCELLED : Транзакция отменена.
}

state "Outbox-сообщение\n(core-transaction)" as ob {
    [*] --> CREATED : Атомарная запись\nв outbox_messages\n(совместно с транзакцией)

    CREATED --> POLLED : Outbox Consumer\nзахватил партицию\nи прочитал сообщение
    POLLED --> PUBLISHED : Успешная публикация\nв Kafka-топик
    POLLED --> RETRY : Kafka недоступен /\nошибка публикации
    RETRY --> POLLED : Повторное чтение\nпри следующем poll
    PUBLISHED --> CLEANED : Удаление по\nрасписанию\n(через 7 дней)

    CLEANED --> [*]

    state CREATED : Сообщение сохранено\nв партицию\n(partition_num 0–7).
    state POLLED : Consumer прочитал\nпакет сообщений.
    state PUBLISHED : Сообщение доставлено\nв Kafka. Смещение\nобновлено.
    state RETRY : Ожидание следующего\nцикла poll\n(через 1 с).
    state CLEANED : Удалено задачей\nOutboxCleanupTask\n(ежедневно в 03:00).
}

state "Запись реестра\n(core-ledger)" as lg {
    [*] --> RECEIVED : Kafka Consumer\nполучил сообщение

    RECEIVED --> DUPLICATE : idempotency_key\nуже существует
    RECEIVED --> SAVED : Новая запись\nсохранена в\ntransaction_ledger
    DUPLICATE --> ACKED : Событие пропущено,\nно подтверждено
    SAVED --> ACKED : Успешная запись,\nсобытие подтверждено
    RECEIVED --> RETRY_LG : Ошибка записи в БД
    RETRY_LG --> RECEIVED : Повтор (до 3 раз,\nинтервал 1 с)
    RETRY_LG --> DLQ : Исчерпаны попытки

    ACKED --> [*]
    DLQ --> [*]

    state RECEIVED : Десериализация\nTransactionKafkaMessage.
    state SAVED : Запись создана\nв ledger_db.
    state DUPLICATE : Дубликат обнаружен\nпо idempotency_key.
    state ACKED : acknowledge()\nвызван.
    state RETRY_LG : DefaultErrorHandler:\nfixed backoff 1s, max 3.
    state DLQ : Сообщение передано\nв обработчик ошибок.
}

tx -[hidden]right-> ob
ob -[hidden]right-> lg
@enduml
```

---

## 12. Диаграмма потоков данных

```plantuml
@startuml
skinparam shadowing false
skinparam rectangle {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
}
skinparam ArrowColor #000000
skinparam nodesep 50
skinparam ranksep 40

rectangle "Пользователь" as user

rectangle "REST API\ncore-transaction" as api
rectangle "Сервис\nтранзакций" as svc
rectangle "transaction_db" as tdb

rectangle "Outbox\nConsumer" as outbox
rectangle "Kafka" as kafka

rectangle "Kafka\nConsumer" as lgcons
rectangle "Сервис\nреестра" as lgsvc
rectangle "ledger_db" as ldb

rectangle "REST API\ncore-ledger" as lgapi

user --> api : "TransferRequest (JSON)"
api --> svc : "DTO запроса"
svc --> tdb : "Транзакция +\nOutbox-событие"
tdb --> outbox : "Чтение outbox_messages"
outbox --> kafka : "TransactionKafkaMessage"
kafka --> lgcons : "Сообщение из топика"
lgcons --> lgsvc : "TransactionKafkaMessage"
lgsvc --> ldb : "INSERT в transaction_ledger"
ldb --> lgapi : "TransactionLedgerResponse"
lgapi --> user : "История / Отчёт"
@enduml
```

---

## 13. ER-диаграмма: transaction_db

```plantuml
@startuml
skinparam shadowing false
skinparam linetype ortho
skinparam nodesep 60
skinparam ranksep 50
skinparam entity {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
    FontSize 11
}
skinparam ArrowColor #000000
skinparam ArrowFontSize 10

hide circle
title ER-диаграмма: transaction_db

entity "users" as users {
    * id : BIGSERIAL <<PK>>
    --
    * first_name : VARCHAR(100)
    * last_name : VARCHAR(100)
      patronymic : VARCHAR(100)
    * phone_number : VARCHAR(20) <<UQ>>
      email : VARCHAR(150)
    * created_at : TIMESTAMPTZ
    * updated_at : TIMESTAMPTZ
}

entity "accounts" as accounts {
    * id : BIGSERIAL <<PK>>
    --
    * user_id : BIGINT <<FK>>
    * account_number : VARCHAR(20) <<UQ>>
    * account_type : VARCHAR(30)
    * currency : VARCHAR(3)
    * balance : NUMERIC(19,4)
    * is_active : BOOLEAN
    * created_at : TIMESTAMPTZ
    * updated_at : TIMESTAMPTZ
}

entity "cards" as cards {
    * id : BIGSERIAL <<PK>>
    --
    * account_id : BIGINT <<FK>>
    * card_number : VARCHAR(19) <<UQ>>
    * expiry_date : DATE
    * is_active : BOOLEAN
    * created_at : TIMESTAMPTZ
}

entity "transactions" as transactions {
    * id : BIGSERIAL <<PK>>
    --
    * idempotency_key : UUID <<UQ>>
    * transaction_type : VARCHAR(30)
    * status : VARCHAR(20)
      source_account_id : BIGINT <<FK>>
      destination_account_id : BIGINT <<FK>>
    * amount : NUMERIC(19,4)
    * currency : VARCHAR(3)
      description : VARCHAR(500)
      error_message : VARCHAR(500)
    * created_at : TIMESTAMPTZ
      completed_at : TIMESTAMPTZ
}

entity "outbox_messages" as outbox {
    * id : BIGSERIAL
    * partition_num : INTEGER
    --
    * aggregate_type : VARCHAR(100)
    * aggregate_id : VARCHAR(100)
    * event_type : VARCHAR(100)
    * partition_key : VARCHAR(100)
    * payload : JSONB
    * created_at : TIMESTAMPTZ
    ..
    PK(id, partition_num)
    LIST PARTITION BY partition_num
    (p0 .. p7)
}

entity "outbox_consumer_offsets" as offsets {
    * id : BIGSERIAL <<PK>>
    --
    * consumer_group : VARCHAR(100)
    * partition_num : INTEGER
    * last_offset : BIGINT
    * updated_at : TIMESTAMPTZ
    ..
    UQ(consumer_group, partition_num)
}

entity "outbox_partition_locks" as locks {
    * id : BIGSERIAL <<PK>>
    --
    * consumer_group : VARCHAR(100)
    * partition_num : INTEGER
    * locked_by : VARCHAR(200)
    * locked_at : TIMESTAMPTZ
    * expires_at : TIMESTAMPTZ
    ..
    UQ(consumer_group, partition_num)
}

users        ||--o{  accounts     : "user_id"
accounts     ||--o{  cards        : "account_id"
accounts     |o--o{  transactions : "source_account_id"
accounts     ||--o{  transactions : "destination_account_id"
@enduml
```

---

## 14. ER-диаграмма: ledger_db

```plantuml
@startuml
skinparam shadowing false
skinparam entity {
    BackgroundColor #FFFFFF
    BorderColor #000000
    FontColor #000000
    FontSize 11
}
skinparam ArrowColor #000000

hide circle
title ER-диаграмма: ledger_db

entity "transaction_ledger" as ledger {
    * id : BIGSERIAL <<PK>>
    --
    * idempotency_key : VARCHAR(255) <<UQ>>
    * event_type : VARCHAR(50)
    * source_transaction_id : BIGINT <<IDX>>
    * transaction_type : VARCHAR(30)
      source_account_number : VARCHAR(20) <<IDX>>
    * destination_account_number : VARCHAR(20) <<IDX>>
    * amount : NUMERIC(19,4)
    * currency : VARCHAR(3)
    * status : VARCHAR(20)
    * aggregate_id : VARCHAR(255)
    * partition_key : VARCHAR(255)
    * source_message_id : BIGINT
    * event_created_at : TIMESTAMPTZ <<IDX>>
    * received_at : TIMESTAMPTZ
}

@enduml
```

---

## 15. Алгоритм обработки финансовой транзакции (дебет-кредит)

```plantuml
@startuml
skinparam shadowing false
skinparam ActivityBackgroundColor #FFFFFF
skinparam ActivityBorderColor #000000
skinparam ArrowColor #000000
skinparam DiamondBackgroundColor #FFFFFF
skinparam DiamondBorderColor #000000

title Алгоритм обработки транзакции (дебет-кредит)

start

:Получить запрос (idempotencyKey, source, dest, amount);

if (Транзакция с таким ключом\nуже существует?) then (да)
    :Вернуть сохранённый результат;
    stop
else (нет)
endif

:Блокировка счетов (SELECT FOR UPDATE)\nв алфавитном порядке по номеру;

if (Счета существуют и активны?) then (да)
else (нет)
    :BusinessException;
    stop
endif

if (Баланс >= сумма?) then (да)
else (нет)
    :BusinessException (недостаточно средств);
    stop
endif

fork
    :source.balance -= amount;
fork again
    :dest.balance += amount;
end fork

:Создать TransactionEntity (COMPLETED);
:Сохранить событие в outbox_messages (JSONB);
:Вернуть TransactionResponse;

stop
@enduml
```

---

## 16. Алгоритм работы Outbox Consumer (цикл обработки одной партиции)

```plantuml
@startuml
skinparam shadowing false
skinparam ActivityBackgroundColor #FFFFFF
skinparam ActivityBorderColor #000000
skinparam ArrowColor #000000
skinparam DiamondBackgroundColor #FFFFFF
skinparam DiamondBorderColor #000000

title Алгоритм Outbox Consumer (одна партиция)

start

if (Consumer запущен?) then (да)
else (нет)
    stop
endif

:tryAcquireLock(partition, instanceId, TTL);

if (Блокировка захвачена?) then (да)
else (нет)
    stop
endif

:Прочитать currentOffset из outbox_consumer_offsets;
:Загрузить пакет сообщений (limit = 50, offset > currentOffset);

if (Пакет пуст?) then (да)
    stop
else (нет)
endif

while (Есть сообщения?) is (да)
    :handler.handle(eventType, payload, metadata);
    if (Успех?) then (да)
        :lastSuccessId = message.id;
    else (нет)
        :Прервать цикл;
        break
    endif
endwhile (нет)

:upsertOffset(partition, lastSuccessId);

stop
@enduml
```

---

## 17. Алгоритм идемпотентного потребления Kafka-событий

```plantuml
@startuml
skinparam shadowing false
skinparam ActivityBackgroundColor #FFFFFF
skinparam ActivityBorderColor #000000
skinparam ArrowColor #000000
skinparam DiamondBackgroundColor #FFFFFF
skinparam DiamondBorderColor #000000

title Алгоритм потребления Kafka-событий (core-ledger)

start

:Получить ConsumerRecord из топика;
:Извлечь TransactionKafkaMessage и idempotencyKey;

if (Запись с таким ключом\nуже есть в реестре?) then (да)
    :Инкремент ledger.events.duplicates;
else (нет)
    :Создать TransactionLedgerEntity из полей сообщения;
    :Сохранить в transaction_ledger;
    :Инкремент ledger.events.processed;
endif

:acknowledgment.acknowledge();

stop
@enduml
```

---

## 18. Алгоритм генерации номера счёта

```plantuml
@startuml
skinparam shadowing false
skinparam ActivityBackgroundColor #FFFFFF
skinparam ActivityBorderColor #000000
skinparam ArrowColor #000000
skinparam DiamondBackgroundColor #FFFFFF
skinparam DiamondBorderColor #000000

title Алгоритм генерации номера счёта

start

:Получить AccountType;

switch (Тип счёта?)
case (CHECKING)
    :prefix="1", seq=seq_account_checking;
case (SAVINGS)
    :prefix="2", seq=seq_account_savings;
case (DEPOSIT)
    :prefix="3", seq=seq_account_deposit;
case (BROKERAGE)
    :prefix="4", seq=seq_account_brokerage;
endswitch

:seqValue = nextval(seq) через JdbcTemplate;
:accountNumber = prefix + padLeft(seqValue, 19, "0");
:Вернуть accountNumber (20 символов);

stop
@enduml
```
