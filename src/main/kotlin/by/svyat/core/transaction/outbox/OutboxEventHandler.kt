package by.svyat.core.transaction.outbox

import by.svyat.core.transaction.outbox.dto.OutboxEventMetadata
import by.svyat.core.transaction.outbox.enums.OutboxAggregateType

/**
 * Стратегия обработки outbox-событий.
 *
 * Каждая реализация обрабатывает события определённых типов агрегатов.
 * Консьюмер автоматически обнаруживает все Spring-бины, реализующие этот интерфейс,
 * и маршрутизирует сообщения по [supportedAggregateTypes].
 */
interface OutboxEventHandler {

    /** Типы агрегатов, которые обрабатывает данный хэндлер */
    fun supportedAggregateTypes(): List<OutboxAggregateType>

    /** Обработка события */
    fun handle(eventType: String, payload: String, metadata: OutboxEventMetadata)
}
