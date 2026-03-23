package by.svyat.core.transaction.api.dto.outbox

import java.time.OffsetDateTime

data class TransactionKafkaMessage(
    val idempotencyKey: String,
    val eventType: String,
    val payload: TransactionOutboxPayload,
    val metadata: KafkaEventMetadata
)

data class KafkaEventMetadata(
    val aggregateId: String,
    val partitionKey: String,
    val sourceMessageId: Long,
    val createdAt: OffsetDateTime
)
