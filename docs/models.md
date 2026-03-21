# Модели данных

## Сущности

### UserEntity

| Поле        | Тип              | Ограничения                | Описание              |
|-------------|------------------|----------------------------|-----------------------|
| id          | Long             | PK, auto-generated         | ID пользователя       |
| firstName   | String           | NOT NULL, max 100          | Имя                   |
| lastName    | String           | NOT NULL, max 100          | Фамилия               |
| patronymic  | String?          | max 100                    | Отчество              |
| phoneNumber | String           | NOT NULL, UNIQUE, max 20   | Номер телефона        |
| email       | String?          | max 150                    | Электронная почта     |
| createdAt   | OffsetDateTime   | NOT NULL, immutable        | Дата создания         |
| updatedAt   | OffsetDateTime   | NOT NULL                   | Дата обновления       |
| accounts    | List\<Account\>  | OneToMany, LAZY            | Связанные счета       |

### AccountEntity

| Поле          | Тип              | Ограничения                    | Описание           |
|---------------|------------------|--------------------------------|--------------------|
| id            | Long             | PK, auto-generated             | ID счета           |
| user          | UserEntity       | ManyToOne, NOT NULL, LAZY      | Владелец           |
| accountNumber | String           | NOT NULL, UNIQUE, max 20       | Номер счета (авто-генерация) |
| accountType   | AccountType      | NOT NULL, ENUM                 | Тип счета          |
| currency      | String           | NOT NULL, max 3, default "RUB" | Валюта             |
| balance       | BigDecimal       | NOT NULL, NUMERIC(19,4), CHECK >= 0 | Баланс        |
| isActive      | Boolean          | NOT NULL, default true         | Активен ли         |
| createdAt     | OffsetDateTime   | NOT NULL, immutable            | Дата создания      |
| updatedAt     | OffsetDateTime   | NOT NULL                       | Дата обновления    |
| cards         | List\<Card\>     | OneToMany, LAZY                | Привязанные карты  |

### TransactionEntity

| Поле               | Тип               | Ограничения                | Описание                  |
|--------------------|--------------------|----------------------------|---------------------------|
| id                 | Long               | PK, auto-generated         | ID транзакции             |
| idempotencyKey     | UUID               | NOT NULL, UNIQUE           | Ключ идемпотентности      |
| transactionType    | TransactionType    | NOT NULL, ENUM             | Тип транзакции            |
| status             | TransactionStatus  | NOT NULL, ENUM, default PENDING | Статус               |
| sourceAccount      | AccountEntity?     | ManyToOne, LAZY            | Счет-источник (nullable)  |
| destinationAccount | AccountEntity?     | ManyToOne, LAZY            | Счет-назначение (nullable)|
| amount             | BigDecimal         | NOT NULL, NUMERIC(19,4)    | Сумма                     |
| currency           | String             | NOT NULL, max 3            | Валюта                    |
| description        | String?            | max 500                    | Описание                  |
| errorMessage       | String?            | max 500                    | Сообщение об ошибке       |
| createdAt          | OffsetDateTime     | NOT NULL, immutable        | Дата создания             |
| completedAt        | OffsetDateTime?    | —                          | Дата завершения           |

### CardEntity

| Поле       | Тип            | Ограничения                | Описание            |
|------------|----------------|----------------------------|---------------------|
| id         | Long           | PK, auto-generated         | ID карты            |
| account    | AccountEntity  | ManyToOne, NOT NULL, LAZY  | Привязанный счет    |
| cardNumber | String         | NOT NULL, UNIQUE, max 19   | Номер карты         |
| expiryDate | LocalDate      | NOT NULL                   | Дата истечения      |
| isActive   | Boolean        | NOT NULL, default true     | Активна ли          |
| createdAt  | OffsetDateTime | NOT NULL, immutable        | Дата создания       |

---

## Компоненты

### AccountNumberGenerator

Генерирует уникальные номера счетов через PostgreSQL sequences.

- **Формат**: `<префикс><19 цифр>` = 20 символов
- **Префиксы**: CHECKING→"1", SAVINGS→"2", DEPOSIT→"3", BROKERAGE→"4"
- **Sequences**: `seq_account_checking`, `seq_account_savings`, `seq_account_deposit`, `seq_account_brokerage`
- **Уникальность** гарантирована sequence (UNIQUE constraint на `accountNumber` создает B-tree индекс)

Пример: `"1000000000000000001"` (первый CHECKING), `"2000000000000000001"` (первый SAVINGS)

---

## DTO

### CreateAccountRequest

| Поле        | Тип    | Обязательное | Валидация       | Описание                              |
|-------------|--------|:------------:|-----------------|---------------------------------------|
| userId      | Long   | да           | NotNull         | ID владельца счета                    |
| accountType | String | да           | NotBlank        | Тип счета: CHECKING, SAVINGS, DEPOSIT, BROKERAGE |
| currency    | String | нет          | NotBlank, max 3 | Валюта (по умолчанию `"RUB"`)         |

> `accountNumber` не передаётся — генерируется автоматически через `AccountNumberGenerator`.

### TransactionResponse

| Поле                     | Тип              | Описание                              |
|--------------------------|------------------|---------------------------------------|
| id                       | Long             | ID транзакции                         |
| idempotencyKey           | UUID             | Ключ идемпотентности                  |
| transactionType          | String           | Тип транзакции (enum name)            |
| status                   | String           | Статус: PENDING, COMPLETED, FAILED, CANCELLED |
| sourceAccountNumber      | String?          | Номер счета-источника (null для кредитных операций) |
| destinationAccountNumber | String?          | Номер счета-назначения                |
| amount                   | BigDecimal       | Сумма                                 |
| currency                 | String           | Валюта                                |
| description              | String?          | Описание                              |
| errorMessage             | String?          | Сообщение об ошибке (при FAILED)      |
| createdAt                | OffsetDateTime   | Дата создания                         |
| completedAt              | OffsetDateTime?  | Дата завершения                       |

---

## Перечисления (Enums)

### AccountType

| Значение   | Префикс | Описание            |
|------------|---------|---------------------|
| CHECKING   | 1       | Расчетный счет      |
| SAVINGS    | 2       | Накопительный счет  |
| DEPOSIT    | 3       | Депозитный счет     |
| BROKERAGE  | 4       | Брокерский счет     |

### TransactionType

| Значение           | Тип операции | Описание                       |
|--------------------|-------------|--------------------------------|
| TRANSFER_SAVINGS   | debit-credit | Перевод на накопительный счет  |
| TRANSFER_DEPOSIT   | debit-credit | Перевод на депозитный счет     |
| TRANSFER_BROKERAGE | debit-credit | Перевод на брокерский счет     |
| INTERBANK_TRANSFER | debit-credit | Межбанковский перевод          |
| SBP_TRANSFER       | debit-credit | Перевод через СБП              |
| MONEY_GIFT         | credit-only  | Денежный подарок               |
| COMPENSATION       | credit-only  | Компенсация                    |
| CREDIT_PAYMENT     | debit-credit | Кредитный платеж               |

### TransactionStatus

| Значение   | Описание    |
|------------|-------------|
| PENDING    | В обработке |
| COMPLETED  | Завершена   |
| FAILED     | Ошибка      |
| CANCELLED  | Отменена    |

---

## Формат ошибки (ErrorResponse)

Все ошибки возвращаются в едином формате:

| Поле      | Тип              | Описание              |
|-----------|------------------|-----------------------|
| timestamp | OffsetDateTime   | Время ошибки          |
| status    | Int              | HTTP-код              |
| error     | String           | Тип ошибки            |
| message   | String?          | Сообщение             |
| path      | String?          | Путь запроса          |

### Пример

```json
{
  "timestamp": "2026-03-18T12:00:00+03:00",
  "status": 404,
  "error": "Not Found",
  "message": "Account with number 9999999999999999999 not found",
  "path": "/api/v1/accounts/9999999999999999999"
}
```
