# Auth API

Базовый путь: `/api/v1/auth`

## Модель аутентификации

`core-transaction` использует **service-account JWT**, а не пользовательскую аутентификацию. Конечного пользователя аутентифицирует внешний gateway (edge-pattern); транзакционное ядро доверяет только сервисным токенам. В этом сервисе **нет** `/register` и `/login` для пользователей.

Сервис-аккаунт = (`name`, `roles`, `secret`). Список аккаунтов задан в `application.yaml` (секция `auth.service-accounts`). Сервис обменивает `name`+`secret` на JWT через `POST /api/v1/auth/service-token` и шлёт его в заголовке `Authorization: Bearer <token>` ко всем последующим запросам.

## Роли

| Роль       | Доступ                                                                                 |
|------------|----------------------------------------------------------------------------------------|
| `GATEWAY`  | Все write-операции (transfer/*, sbp, interbank, gift, create user/account) + чтение    |
| `SUPPORT`  | `gift`, чтение пользователей/счетов/транзакций                                         |

Авторизация выставлена на уровне методов через `@PreAuthorize` на impl-классах контроллеров. URL-уровень в `SecurityConfig` делает только грубую отсечку: `/api/v1/auth/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**` → `permitAll`; всё остальное → `authenticated()`.

## Формат JWT

- Алгоритм: HMAC-SHA (ключ из `jwt.secret`).
- Claims:
  - `sub` — имя сервис-аккаунта (`gateway`, `support`, …).
  - `roles` — список ролей: `["GATEWAY"]`, `["SUPPORT"]`, `["GATEWAY","SUPPORT"]`.
  - `iat`, `exp` — стандартные.
- **TTL = 1 час** (`jwt.expiration-ms = 3600000`).

Отозвать конкретный токен нельзя — stateless JWT. Чтобы инвалидировать все токены сервиса, поменяйте его `secret` (новые токены он выпустить не сможет, старые проживут до своего `exp`, ≤ 1 час).

## Конфигурация сервис-аккаунтов

`application.yaml`:

```yaml
jwt:
  secret: ${JWT_SECRET:vkr-core-transaction-secret-key-must-be-at-least-256-bits-long!}
  expiration-ms: 3600000

auth:
  service-accounts:
    - name: gateway
      roles: [GATEWAY]
      secret: ${GATEWAY_SECRET:gateway-dev-secret-change-me}
    - name: support
      roles: [SUPPORT]
      secret: ${SUPPORT_SECRET:support-dev-secret-change-me}
```

Пример мультироли (один аккаунт получает обе роли):

```yaml
    - name: super-admin
      roles: [GATEWAY, SUPPORT]
      secret: ${SUPER_ADMIN_SECRET}
```

В проде все секреты должны приходить из env. Дефолты в YAML — только для локальной разработки.

## POST `/api/v1/auth/service-token`

Выдаёт JWT по имени сервис-аккаунта и его секрету.

### Request Body — `ServiceTokenRequest`

| Поле   | Тип    | Обязательное | Описание                          |
|--------|--------|:------------:|-----------------------------------|
| name   | String | да           | Имя сервис-аккаунта               |
| secret | String | да           | Секрет, заданный в `application.yaml` |

### Response — `AuthResponse`

| Поле  | Тип    | Описание |
|-------|--------|----------|
| token | String | JWT (1 час) |

### Коды ответов

| Код | Когда                                                       |
|-----|-------------------------------------------------------------|
| 200 | Токен выдан                                                 |
| 400 | Ошибка валидации (пустое `name`/`secret`)                   |
| 401 | Неверные `name`/`secret`                                    |

### Пример

```bash
TOKEN=$(curl -sX POST http://localhost:8090/api/v1/auth/service-token \
  -H 'Content-Type: application/json' \
  -d '{"name":"gateway","secret":"gateway-dev-secret-change-me"}' | jq -r .token)

curl -X POST http://localhost:8090/api/v1/transactions/gift \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "idempotencyKey": "1363b08b-f563-4d1a-b2e0-b61495567a1d",
    "destinationAccountNumber": "10000000000000000001",
    "amount": 1521,
    "description": "Компенсация"
  }'
```

## Коды ошибок auth

| Код  | Причина                                                                     | Источник                                    |
|------|------------------------------------------------------------------------------|---------------------------------------------|
| 401  | Нет заголовка `Authorization`, истёкший токен, невалидная подпись, нет claim `roles` | `RestAuthenticationEntryPoint`              |
| 403  | Токен валидный, но `@PreAuthorize` требует роль, которой нет в токене        | `GlobalExceptionHandler.handleAccessDenied` |

Оба ответа возвращаются в формате `ErrorResponse` (`timestamp`, `status`, `error`, `message`, `path`).

## Матрица доступа

Дублирует `@PreAuthorize` на контроллерах — даёт обзорную картину для аудита.

| Endpoint                                       | Метод | Требуемая роль          |
|------------------------------------------------|-------|-------------------------|
| `/api/v1/auth/service-token`                   | POST  | (permitAll)             |
| `/api/v1/users`                                | POST  | `GATEWAY`               |
| `/api/v1/users/{id}`                           | GET   | `GATEWAY` или `SUPPORT` |
| `/api/v1/users/phone/{phoneNumber}`            | GET   | `GATEWAY` или `SUPPORT` |
| `/api/v1/accounts`                             | POST  | `GATEWAY`               |
| `/api/v1/accounts/{accountNumber}`             | GET   | `GATEWAY` или `SUPPORT` |
| `/api/v1/accounts/user/{userId}`               | GET   | `GATEWAY` или `SUPPORT` |
| `/api/v1/transactions/savings`                 | POST  | `GATEWAY`               |
| `/api/v1/transactions/deposit`                 | POST  | `GATEWAY`               |
| `/api/v1/transactions/brokerage`               | POST  | `GATEWAY`               |
| `/api/v1/transactions/checking`                | POST  | `GATEWAY`               |
| `/api/v1/transactions/interbank`               | POST  | `GATEWAY`               |
| `/api/v1/transactions/sbp`                     | POST  | `GATEWAY`               |
| `/api/v1/transactions/gift`                    | POST  | `GATEWAY` или `SUPPORT` |
| `/api/v1/transactions/{id}`                    | GET   | `GATEWAY` или `SUPPORT` |
| `/api/v1/transactions/account/{accountNumber}` | GET   | `GATEWAY` или `SUPPORT` |
| `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**` | —     | (permitAll)             |
