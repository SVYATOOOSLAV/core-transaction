package by.svyat.core.transaction.outbox.entity

import by.svyat.core.transaction.entity.OutboxMessageEntity

data class PollResult(val currentOffset: Long, val messages: List<OutboxMessageEntity>)