package by.svyat.core.transaction.outbox

import by.svyat.core.transaction.outbox.enums.OutboxAggregateType

interface OutboxProducer {

    fun publish(
        aggregateType: OutboxAggregateType,
        aggregateId: String,
        eventType: String,
        partitionKey: String,
        payload: Any
    )
}
