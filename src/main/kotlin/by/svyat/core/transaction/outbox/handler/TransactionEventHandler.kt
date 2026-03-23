package by.svyat.core.transaction.outbox.handler

import by.svyat.core.transaction.api.dto.outbox.KafkaEventMetadata
import by.svyat.core.transaction.api.dto.outbox.TransactionKafkaMessage
import by.svyat.core.transaction.api.dto.outbox.TransactionOutboxPayload
import by.svyat.core.transaction.outbox.OutboxEventHandler
import by.svyat.core.transaction.outbox.dto.OutboxEventMetadata
import by.svyat.core.transaction.outbox.enums.OutboxAggregateType
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

@Component
class TransactionEventHandler(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) : OutboxEventHandler {

    override fun supportedAggregateTypes() = listOf(OutboxAggregateType.TRANSACTION)

    override fun handle(eventType: String, payload: String, metadata: OutboxEventMetadata) {
        val transactionPayload = objectMapper.readValue(payload, TransactionOutboxPayload::class.java)
        val idempotencyKey = "${metadata.aggregateId}-${metadata.messageId}"

        val message = TransactionKafkaMessage(
            idempotencyKey = idempotencyKey,
            eventType = eventType,
            payload = transactionPayload,
            metadata = KafkaEventMetadata(
                aggregateId = metadata.aggregateId,
                partitionKey = metadata.partitionKey,
                sourceMessageId = metadata.messageId,
                createdAt = metadata.createdAt
            )
        )

        val result = kafkaTemplate.send(TOPIC, metadata.aggregateId, message)
            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        log.info {
            "Sent to Kafka: topic=$TOPIC, key=${metadata.aggregateId}, " +
                "offset=${result.recordMetadata.offset()}, idempotencyKey=$idempotencyKey"
        }
    }

    companion object {
        const val TOPIC = "core.transactions.request"
        private const val SEND_TIMEOUT_SECONDS = 10L
    }
}
