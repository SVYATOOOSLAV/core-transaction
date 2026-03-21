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
| accountNumber | String           | NOT NULL, UNIQUE, max 20       | Номер счета        |
| accountType   | AccountType      | NOT NULL, ENUM                 | Тип счета          |
| currency      | String           | NOT NULL, max 3, default "RUB" | Валюта             |
| balance       | BigDecimal       | NOT NULL, NUMERIC(19,4)        | Баланс             |
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

## Перечисления (Enums)

### AccountType

| Значение   | Описание            |
|------------|---------------------|
| CHECKING   | Расчетный счет      |
| SAVINGS    | Накопительный счет  |
| DEPOSIT    | Депозитный счет     |
| BROKERAGE  | Брокерский счет     |

### TransactionType

| Значение           | Описание                       |
|--------------------|--------------------------------|
| TRANSFER_SAVINGS   | Перевод на накопительный счет  |
| TRANSFER_DEPOSIT   | Перевод на депозитный счет     |
| TRANSFER_BROKERAGE | Перевод на брокерский счет     |
| INTERBANK_TRANSFER | Межбанковский перевод          |
| SBP_TRANSFER       | Перевод через СБП              |
| MONEY_GIFT         | Денежный подарок               |
| COMPENSATION       | Компенсация                    |
| CREDIT_PAYMENT     | Кредитный платеж               |

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
  "message": "Account with id 99 not found",
  "path": "/api/v1/accounts/99"
}
```
