package by.svyat.core.transaction.outbox.impl

import by.svyat.core.transaction.outbox.configuration.OutboxProperties
import by.svyat.core.transaction.outbox.enums.OutboxAggregateType
import by.svyat.core.transaction.entity.OutboxMessageEntity
import by.svyat.core.transaction.repository.OutboxMessageRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.abs

class OutboxProducerImplTest {

    private val outboxMessageRepository: OutboxMessageRepository = mockk()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val outboxProperties = OutboxProperties(partitionCount = 8)

    private val producer = OutboxProducerImpl(outboxMessageRepository, objectMapper, outboxProperties)

    @Nested
    inner class Publish {

        @Test
        fun `saves message with correct fields`() {
            val messageSlot = slot<OutboxMessageEntity>()
            every { outboxMessageRepository.save(capture(messageSlot)) } answers { firstArg() }

            producer.publish(
                aggregateType = OutboxAggregateType.TRANSACTION,
                aggregateId = "42",
                eventType = "TRANSFER_COMPLETED",
                partitionKey = "1000000000000000001",
                payload = mapOf("transactionId" to 42, "amount" to 500)
            )

            verify(exactly = 1) { outboxMessageRepository.save(any()) }

            val saved = messageSlot.captured
            assertEquals(OutboxAggregateType.TRANSACTION.name, saved.aggregateType)
            assertEquals("42", saved.aggregateId)
            assertEquals("TRANSFER_COMPLETED", saved.eventType)
            assertEquals("1000000000000000001", saved.partitionKey)
        }

        @Test
        fun `computes partition number as abs(hashCode) mod partitionCount`() {
            val messageSlot = slot<OutboxMessageEntity>()
            every { outboxMessageRepository.save(capture(messageSlot)) } answers { firstArg() }

            val partitionKey = "1000000000000000001"
            val expectedPartition = abs(partitionKey.hashCode()) % 8

            producer.publish(
                aggregateType = OutboxAggregateType.TRANSACTION,
                aggregateId = "1",
                eventType = "TRANSFER_COMPLETED",
                partitionKey = partitionKey,
                payload = "test"
            )

            assertEquals(expectedPartition, messageSlot.captured.partitionNum)
        }

        @Test
        fun `partition number is always within valid range`() {
            every { outboxMessageRepository.save(any()) } answers { firstArg() }

            val partitionKeys = listOf(
                "1000000000000000001",
                "2000000000000000001",
                "3000000000000000001",
                "4000000000000000001",
                "short",
                "",
                "very-long-partition-key-that-exceeds-normal-length-" + "x".repeat(100)
            )

            for (key in partitionKeys) {
                val messageSlot = slot<OutboxMessageEntity>()
                every { outboxMessageRepository.save(capture(messageSlot)) } answers { firstArg() }

                producer.publish(
                    aggregateType = OutboxAggregateType.TRANSACTION,
                    aggregateId = "1",
                    eventType = "TRANSFER_COMPLETED",
                    partitionKey = key,
                    payload = "test"
                )

                val partitionNum = messageSlot.captured.partitionNum
                assertTrue(partitionNum in 0 until 8) {
                    "Partition $partitionNum for key '$key' is out of range [0, 8)"
                }
            }
        }

        @Test
        fun `same partition key always maps to same partition`() {
            val partitions = mutableListOf<Int>()
            every { outboxMessageRepository.save(any()) } answers {
                partitions.add(firstArg<OutboxMessageEntity>().partitionNum)
                firstArg()
            }

            repeat(5) {
                producer.publish(
                    aggregateType = OutboxAggregateType.TRANSACTION,
                    aggregateId = it.toString(),
                    eventType = "TRANSFER_COMPLETED",
                    partitionKey = "1000000000000000001",
                    payload = "test"
                )
            }

            assertTrue(partitions.all { it == partitions[0] }) {
                "Expected all partitions to be ${partitions[0]}, but got $partitions"
            }
        }

        @Test
        fun `serializes payload to JSON`() {
            val messageSlot = slot<OutboxMessageEntity>()
            every { outboxMessageRepository.save(capture(messageSlot)) } answers { firstArg() }

            val payload = mapOf(
                "transactionId" to 42L,
                "type" to "TRANSFER_SAVINGS",
                "amount" to 500.0
            )

            producer.publish(
                aggregateType = OutboxAggregateType.TRANSACTION,
                aggregateId = "42",
                eventType = "TRANSFER_COMPLETED",
                partitionKey = "1000000000000000001",
                payload = payload
            )

            val json = messageSlot.captured.payload
            val parsed = objectMapper.readTree(json)

            assertEquals(42, parsed["transactionId"].asLong())
            assertEquals("TRANSFER_SAVINGS", parsed["type"].asText())
            assertEquals(500.0, parsed["amount"].asDouble())
        }

        @Test
        fun `serializes data class payload correctly`() {
            val messageSlot = slot<OutboxMessageEntity>()
            every { outboxMessageRepository.save(capture(messageSlot)) } answers { firstArg() }

            data class TestPayload(val id: Long, val name: String, val active: Boolean)

            producer.publish(
                aggregateType = OutboxAggregateType.ACCOUNT,
                aggregateId = "1",
                eventType = "CREATED",
                partitionKey = "key",
                payload = TestPayload(id = 1, name = "test", active = true)
            )

            val json = messageSlot.captured.payload
            val parsed = objectMapper.readTree(json)

            assertEquals(1L, parsed["id"].asLong())
            assertEquals("test", parsed["name"].asText())
            assertEquals(true, parsed["active"].asBoolean())
        }

        @Test
        fun `respects custom partition count`() {
            val customProperties = OutboxProperties(partitionCount = 4)
            val customProducer = OutboxProducerImpl(outboxMessageRepository, objectMapper, customProperties)

            val messageSlot = slot<OutboxMessageEntity>()
            every { outboxMessageRepository.save(capture(messageSlot)) } answers { firstArg() }

            val partitionKey = "1000000000000000001"
            val expectedPartition = abs(partitionKey.hashCode()) % 4

            customProducer.publish(
                aggregateType = OutboxAggregateType.TRANSACTION,
                aggregateId = "1",
                eventType = "TRANSFER_COMPLETED",
                partitionKey = partitionKey,
                payload = "test"
            )

            assertEquals(expectedPartition, messageSlot.captured.partitionNum)
        }

        @Test
        fun `sets createdAt timestamp`() {
            val messageSlot = slot<OutboxMessageEntity>()
            every { outboxMessageRepository.save(capture(messageSlot)) } answers { firstArg() }

            producer.publish(
                aggregateType = OutboxAggregateType.TRANSACTION,
                aggregateId = "1",
                eventType = "TRANSFER_COMPLETED",
                partitionKey = "key",
                payload = "test"
            )

            val createdAt = messageSlot.captured.createdAt
            assertTrue(createdAt.isBefore(java.time.OffsetDateTime.now().plusSeconds(1))) { "createdAt should be close to now" }
        }
    }
}
