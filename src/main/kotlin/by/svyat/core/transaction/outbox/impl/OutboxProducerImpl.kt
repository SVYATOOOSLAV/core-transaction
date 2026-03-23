package by.svyat.core.transaction.outbox.impl

import by.svyat.core.transaction.outbox.configuration.OutboxProperties
import by.svyat.core.transaction.entity.OutboxMessageEntity
import by.svyat.core.transaction.outbox.enums.OutboxAggregateType
import by.svyat.core.transaction.repository.OutboxMessageRepository
import by.svyat.core.transaction.outbox.OutboxProducer
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import mu.KotlinLogging
import org.springframework.stereotype.Service
import kotlin.math.abs

private val log = KotlinLogging.logger {}

@Service
class OutboxProducerImpl(
    private val outboxMessageRepository: OutboxMessageRepository,
    private val objectMapper: ObjectMapper,
    private val outboxProperties: OutboxProperties,
    private val meterRegistry: MeterRegistry
) : OutboxProducer {

    override fun publish(
        aggregateType: OutboxAggregateType,
        aggregateId: String,
        eventType: String,
        partitionKey: String,
        payload: Any
    ) {
        if (!outboxProperties.producerEnabled) {
            log.debug { "Outbox producer is disabled, skipping: type=$aggregateType, id=$aggregateId, event=$eventType" }
            return
        }

        val sample = Timer.start(meterRegistry)

        val partitionNum = abs(partitionKey.hashCode()) % outboxProperties.partitionCount
        val jsonPayload = objectMapper.writeValueAsString(payload)

        val message = OutboxMessageEntity(
            aggregateType = aggregateType.name,
            aggregateId = aggregateId,
            eventType = eventType,
            partitionKey = partitionKey,
            partitionNum = partitionNum,
            payload = jsonPayload
        )
        outboxMessageRepository.save(message)

        sample.stop(
            Timer.builder("outbox.producer.publish.duration")
                .tag("aggregateType", aggregateType.name)
                .description("Time to serialize and persist outbox message")
                .register(meterRegistry)
        )

        publishedCounter(aggregateType.name, eventType).increment()

        log.debug { "Outbox message published: type=$aggregateType, id=$aggregateId, event=$eventType, partition=$partitionNum" }
    }

    private fun publishedCounter(aggregateType: String, eventType: String) =
        io.micrometer.core.instrument.Counter.builder("outbox.producer.published")
            .tag("aggregateType", aggregateType)
            .tag("eventType", eventType)
            .description("Total number of outbox messages published")
            .register(meterRegistry)
}
