# Метрики

Приложение использует **Micrometer** с реестром **Prometheus**. Все метрики доступны через эндпоинт:

```
GET /actuator/prometheus
```

Глобальный тег `application=core-transaction` применяется ко всем метрикам автоматически.

---

## Транзакции

Источник: `TransactionServiceImpl`

| Метрика | Тип | Теги | Описание |
|---------|-----|------|----------|
| `transactions.total` | Counter | `type`, `status` | Общее количество транзакций |
| `accounts.balance` | Gauge | `accountNumber` | Текущий баланс счёта после дебетовой операции |

**Теги `transactions.total`:**
- `type` — тип транзакции: `TRANSFER_SAVINGS`, `TRANSFER_DEPOSIT`, `TRANSFER_BROKERAGE`, `INTERBANK_TRANSFER`, `SBP_TRANSFER`, `MONEY_GIFT`, `COMPENSATION`, `CREDIT_PAYMENT`
- `status` — результат: `COMPLETED`, `FAILED`

---

## Outbox Producer

Источник: `OutboxProducerImpl`

| Метрика | Тип | Теги | Описание |
|---------|-----|------|----------|
| `outbox.producer.published` | Counter | `aggregateType`, `eventType` | Количество опубликованных сообщений в outbox |
| `outbox.producer.publish.duration` | Timer | `aggregateType` | Время сериализации payload и сохранения сообщения в БД |

**Теги:**
- `aggregateType` — тип агрегата: `TRANSACTION`, `ACCOUNT`
- `eventType` — тип события: `TRANSFER_COMPLETED`, `TRANSFER_FAILED`, `BALANCE_CHANGED`, `ACCOUNT_CREATED`, `ACCOUNT_DEACTIVATED`

**Примеры Prometheus-запросов:**

```promql
# RPS публикации в outbox
rate(outbox_producer_published_total[5m])

# p99 длительности публикации
histogram_quantile(0.99, rate(outbox_producer_publish_duration_seconds_bucket[5m]))
```

---

## Outbox Consumer

Источник: `OutboxConsumerImpl`

### Обработка сообщений

| Метрика | Тип | Теги | Описание |
|---------|-----|------|----------|
| `outbox.consumer.processed` | Counter | `aggregateType`, `eventType`, `status` | Количество обработанных сообщений по статусу |
| `outbox.consumer.process.duration` | Timer | `aggregateType`, `eventType` | Время выполнения хэндлера для одного сообщения |
| `outbox.consumer.lag` | Timer | `partition` | Лаг между вставкой сообщения (`createdAt`) и моментом обработки |

**Теги `status`:**
- `success` — хэндлер обработал сообщение успешно
- `error` — хэндлер выбросил исключение
- `skipped` — для `aggregateType` не зарегистрирован хэндлер

**Примеры Prometheus-запросов:**

```promql
# Количество ошибок обработки за последние 5 минут
increase(outbox_consumer_processed_total{status="error"}[5m])

# Средний лаг обработки
rate(outbox_consumer_lag_seconds_sum[5m]) / rate(outbox_consumer_lag_seconds_count[5m])

# p95 лага
histogram_quantile(0.95, rate(outbox_consumer_lag_seconds_bucket[5m]))

# p99 времени обработки хэндлером
histogram_quantile(0.99, rate(outbox_consumer_process_duration_seconds_bucket[5m]))
```

### Поллинг

| Метрика | Тип | Теги | Описание |
|---------|-----|------|----------|
| `outbox.consumer.poll.duration` | Timer | `partition` | Длительность полного цикла поллинга (захват блокировки + выборка + обработка + обновление офсета) |
| `outbox.consumer.poll.batch.size` | DistributionSummary | `partition` | Количество сообщений, выбранных за один полл |

**Примеры Prometheus-запросов:**

```promql
# Среднее количество сообщений в батче
rate(outbox_consumer_poll_batch_size_sum[5m]) / rate(outbox_consumer_poll_batch_size_count[5m])

# p99 длительности полла
histogram_quantile(0.99, rate(outbox_consumer_poll_duration_seconds_bucket[5m]))
```

### Состояние консьюмера

| Метрика | Тип | Теги | Описание |
|---------|-----|------|----------|
| `outbox.consumer.offset` | Gauge | `partition` | Текущий офсет (ID последнего обработанного сообщения) по каждой партиции |
| `outbox.consumer.lock.acquired` | Counter | `partition`, `result` | Результаты попыток захвата блокировки партиции |
| `outbox.consumer.errors` | Counter | `partition` | Необработанные ошибки в цикле поллинга (ошибки на уровне транзакции/БД) |

**Теги `result` (lock.acquired):**
- `success` — блокировка успешно захвачена
- `failure` — блокировка занята другим инстансом

**Примеры Prometheus-запросов:**

```promql
# Текущий офсет по партициям
outbox_consumer_offset

# Доля неудачных захватов блокировки
rate(outbox_consumer_lock_acquired_total{result="failure"}[5m])
/ rate(outbox_consumer_lock_acquired_total[5m])

# Количество ошибок поллинга
increase(outbox_consumer_errors_total[5m])
```

---

## Инфраструктурные метрики (Spring Boot Actuator)

Spring Boot Actuator автоматически предоставляет метрики:

| Метрика | Описание |
|---------|----------|
| `http.server.requests` | Длительность и количество HTTP-запросов (по URI, методу, статусу) |
| `jvm.memory.*` | Использование памяти JVM (heap, non-heap, буферы) |
| `jvm.gc.*` | Статистика сборки мусора |
| `jvm.threads.*` | Количество потоков |
| `system.cpu.*` | Загрузка CPU |
| `hikaricp.*` | Метрики пула соединений HikariCP |
| `jdbc.connections.*` | Активные JDBC-соединения |

---

## Конфигурация

Эндпоинты метрик настраиваются в `application.yaml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: core-transaction
```
