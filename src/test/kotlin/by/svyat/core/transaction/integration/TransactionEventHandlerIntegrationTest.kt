package by.svyat.core.transaction.integration

import by.svyat.core.transaction.IntegrationTestBase
import by.svyat.core.transaction.api.dto.outbox.TransactionKafkaMessage
import by.svyat.core.transaction.api.dto.outbox.TransactionOutboxPayload
import by.svyat.core.transaction.entity.enums.TransactionStatus
import by.svyat.core.transaction.entity.enums.TransactionType
import by.svyat.core.transaction.outbox.dto.OutboxEventMetadata
import by.svyat.core.transaction.outbox.handler.TransactionEventHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.kafka.KafkaContainer
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicLong

class TransactionEventHandlerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var transactionEventHandler: TransactionEventHandler

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    companion object {
        private val messageIdCounter = AtomicLong(System.nanoTime())

        @JvmStatic
        val kafka: KafkaContainer = KafkaContainer("apache/kafka:3.7.0")
            .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun kafkaProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
        }
    }

    private fun uniqueMessageId(): Long = messageIdCounter.incrementAndGet()

    private fun createStringConsumer(): KafkaConsumer<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "test-consumer-${System.nanoTime()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name
        )
        return KafkaConsumer(props)
    }

    private fun pollAllMessages(
        consumer: KafkaConsumer<String, String>,
        timeoutMs: Long = 10_000
    ): List<ConsumerRecord<String, String>> {
        val results = mutableListOf<ConsumerRecord<String, String>>()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val records = consumer.poll(Duration.ofSeconds(1))
            records.forEach { results.add(it) }
            if (results.isNotEmpty() && records.isEmpty) break
        }
        return results
    }

    private fun parseMessage(json: String): TransactionKafkaMessage =
        objectMapper.readValue(json, TransactionKafkaMessage::class.java)

    private fun samplePayload() = TransactionOutboxPayload(
        transactionId = 42,
        type = TransactionType.TRANSFER_SAVINGS,
        sourceAccountNumber = "10000000000000000001",
        destinationAccountNumber = "20000000000000000001",
        amount = BigDecimal("1500.00"),
        currency = "RUB",
        status = TransactionStatus.COMPLETED
    )

    private fun sampleMetadata(
        messageId: Long = uniqueMessageId(),
        aggregateId: String = "42"
    ) = OutboxEventMetadata(
        messageId = messageId,
        aggregateType = "TRANSACTION",
        aggregateId = aggregateId,
        partitionKey = "10000000000000000001",
        partitionNum = 0,
        createdAt = OffsetDateTime.now()
    )

    @Nested
    inner class SendToKafka {

        @Test
        fun `handler sends message to kafka topic with correct structure`() {
            val msgId = uniqueMessageId()
            val payload = samplePayload()
            val metadata = sampleMetadata(messageId = msgId)
            val payloadJson = objectMapper.writeValueAsString(payload)

            transactionEventHandler.handle("TRANSFER_COMPLETED", payloadJson, metadata)

            val consumer = createStringConsumer()
            consumer.subscribe(listOf(TransactionEventHandler.TOPIC))
            try {
                val records = pollAllMessages(consumer)
                val message = records
                    .map { parseMessage(it.value()) }
                    .first { it.idempotencyKey == "42-$msgId" }

                assertEquals("TRANSFER_COMPLETED", message.eventType)
                assertEquals(42L, message.payload.transactionId)
                assertEquals(TransactionType.TRANSFER_SAVINGS, message.payload.type)
                assertEquals(BigDecimal("1500.00"), message.payload.amount)
                assertEquals("RUB", message.payload.currency)
                assertEquals(TransactionStatus.COMPLETED, message.payload.status)
                assertEquals("10000000000000000001", message.payload.sourceAccountNumber)
                assertEquals("20000000000000000001", message.payload.destinationAccountNumber)

                assertEquals("42", message.metadata.aggregateId)
                assertEquals("10000000000000000001", message.metadata.partitionKey)
                assertEquals(msgId, message.metadata.sourceMessageId)

                val record = records.first { parseMessage(it.value()).idempotencyKey == "42-$msgId" }
                assertEquals("42", record.key())
            } finally {
                consumer.close()
            }
        }

        @Test
        fun `handler sends multiple messages preserving order`() {
            val msgId1 = uniqueMessageId()
            val msgId2 = uniqueMessageId()
            val msgId3 = uniqueMessageId()
            val payload = samplePayload()
            val payloadJson = objectMapper.writeValueAsString(payload)

            transactionEventHandler.handle("TRANSFER_COMPLETED", payloadJson, sampleMetadata(messageId = msgId1))
            transactionEventHandler.handle("TRANSFER_COMPLETED", payloadJson, sampleMetadata(messageId = msgId2))
            transactionEventHandler.handle("TRANSFER_COMPLETED", payloadJson, sampleMetadata(messageId = msgId3))

            val consumer = createStringConsumer()
            consumer.subscribe(listOf(TransactionEventHandler.TOPIC))
            try {
                val records = pollAllMessages(consumer)
                val expectedKeys = setOf("42-$msgId1", "42-$msgId2", "42-$msgId3")
                val messages = records
                    .map { parseMessage(it.value()) }
                    .filter { it.idempotencyKey in expectedKeys }

                assertEquals(3, messages.size, "Expected 3 messages")
                assertEquals("42-$msgId1", messages[0].idempotencyKey)
                assertEquals("42-$msgId2", messages[1].idempotencyKey)
                assertEquals("42-$msgId3", messages[2].idempotencyKey)
            } finally {
                consumer.close()
            }
        }
    }

    @Nested
    inner class IdempotencyKey {

        @Test
        fun `same outbox message produces deterministic idempotency key`() {
            val msgId = uniqueMessageId()
            val payload = samplePayload()
            val payloadJson = objectMapper.writeValueAsString(payload)
            val metadata = sampleMetadata(messageId = msgId)

            transactionEventHandler.handle("TRANSFER_COMPLETED", payloadJson, metadata)
            transactionEventHandler.handle("TRANSFER_COMPLETED", payloadJson, metadata)

            val consumer = createStringConsumer()
            consumer.subscribe(listOf(TransactionEventHandler.TOPIC))
            try {
                val records = pollAllMessages(consumer)
                val messages = records
                    .map { parseMessage(it.value()) }
                    .filter { it.idempotencyKey == "42-$msgId" }

                assertEquals(2, messages.size, "Expected 2 messages with same idempotency key")
                assertEquals(messages[0].idempotencyKey, messages[1].idempotencyKey)
            } finally {
                consumer.close()
            }
        }

        @Test
        fun `different outbox messages produce different idempotency keys`() {
            val msgId1 = uniqueMessageId()
            val msgId2 = uniqueMessageId()
            val payload = samplePayload()
            val payloadJson = objectMapper.writeValueAsString(payload)

            transactionEventHandler.handle("TRANSFER_COMPLETED", payloadJson, sampleMetadata(messageId = msgId1))
            transactionEventHandler.handle("TRANSFER_COMPLETED", payloadJson, sampleMetadata(messageId = msgId2))

            val consumer = createStringConsumer()
            consumer.subscribe(listOf(TransactionEventHandler.TOPIC))
            try {
                val records = pollAllMessages(consumer)
                val expectedKeys = setOf("42-$msgId1", "42-$msgId2")
                val messages = records
                    .map { parseMessage(it.value()) }
                    .filter { it.idempotencyKey in expectedKeys }

                assertEquals(2, messages.size)
                assertNotEquals(messages[0].idempotencyKey, messages[1].idempotencyKey)
            } finally {
                consumer.close()
            }
        }
    }

    @Nested
    inner class PayloadDeserialization {

        @Test
        fun `handler correctly deserializes outbox payload string to object`() {
            val msgId = uniqueMessageId()
            val payloadJson = """
                {
                    "transactionId": 99,
                    "type": "SBP_TRANSFER",
                    "sourceAccountNumber": "10000000000000000005",
                    "destinationAccountNumber": "20000000000000000010",
                    "amount": 250.50,
                    "currency": "RUB",
                    "status": "PENDING"
                }
            """.trimIndent()

            transactionEventHandler.handle("TRANSFER_COMPLETED", payloadJson, sampleMetadata(messageId = msgId))

            val consumer = createStringConsumer()
            consumer.subscribe(listOf(TransactionEventHandler.TOPIC))
            try {
                val records = pollAllMessages(consumer)
                val message = records
                    .map { parseMessage(it.value()) }
                    .first { it.idempotencyKey == "42-$msgId" }

                assertEquals(99L, message.payload.transactionId)
                assertEquals(TransactionType.SBP_TRANSFER, message.payload.type)
                assertEquals("10000000000000000005", message.payload.sourceAccountNumber)
                assertEquals("20000000000000000010", message.payload.destinationAccountNumber)
                assertEquals(BigDecimal("250.50"), message.payload.amount)
                assertEquals("RUB", message.payload.currency)
                assertEquals(TransactionStatus.PENDING, message.payload.status)
            } finally {
                consumer.close()
            }
        }

        @Test
        fun `handler correctly deserializes credit-only payload with null source`() {
            val msgId = uniqueMessageId()
            val payloadJson = """
                {
                    "transactionId": 100,
                    "type": "MONEY_GIFT",
                    "sourceAccountNumber": null,
                    "destinationAccountNumber": "20000000000000000001",
                    "amount": 1000.00,
                    "currency": "RUB",
                    "status": "COMPLETED"
                }
            """.trimIndent()

            transactionEventHandler.handle("TRANSFER_COMPLETED", payloadJson, sampleMetadata(messageId = msgId))

            val consumer = createStringConsumer()
            consumer.subscribe(listOf(TransactionEventHandler.TOPIC))
            try {
                val records = pollAllMessages(consumer)
                val message = records
                    .map { parseMessage(it.value()) }
                    .first { it.idempotencyKey == "42-$msgId" }

                assertNull(message.payload.sourceAccountNumber)
                assertEquals(TransactionType.MONEY_GIFT, message.payload.type)
            } finally {
                consumer.close()
            }
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `handler throws exception on invalid payload json`() {
            val invalidJson = "not a json"
            val metadata = sampleMetadata()

            assertThrows(Exception::class.java) {
                transactionEventHandler.handle("TRANSFER_COMPLETED", invalidJson, metadata)
            }
        }
    }
}
