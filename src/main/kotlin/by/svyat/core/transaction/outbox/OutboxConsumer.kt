package by.svyat.core.transaction.outbox

import org.springframework.context.SmartLifecycle

/**
 * Консьюмер outbox-сообщений.
 *
 * Автоматически обнаруживает все бины [OutboxEventHandler] через Spring DI
 * и маршрутизирует сообщения на основе [OutboxEventHandler.supportedAggregateTypes].
 */
interface OutboxConsumer : SmartLifecycle
