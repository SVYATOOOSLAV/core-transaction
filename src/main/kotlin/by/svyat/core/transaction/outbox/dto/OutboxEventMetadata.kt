package by.svyat.core.transaction.outbox.dto

import java.time.OffsetDateTime

data class OutboxEventMetadata(
    val messageId: Long,
    val aggregateType: String,
    val aggregateId: String,
    val partitionKey: String,
    val partitionNum: Int,
    val createdAt: OffsetDateTime
)