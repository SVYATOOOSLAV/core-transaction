package by.svyat.core.transaction.outbox.handler

import by.svyat.core.transaction.outbox.OutboxEventHandler
import by.svyat.core.transaction.outbox.dto.OutboxEventMetadata
import by.svyat.core.transaction.outbox.enums.OutboxAggregateType
import mu.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class TransactionEventHandler : OutboxEventHandler {

    override fun supportedAggregateTypes() = listOf(OutboxAggregateType.TRANSACTION)

    override fun handle(eventType: String, payload: String, metadata: OutboxEventMetadata) {
        log.info { "Processing outbox event: type=$eventType, aggregateId=${metadata.aggregateId}, partition=${metadata.partitionNum}, payload=$payload" }
    }
}
