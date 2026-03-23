package by.svyat.core.transaction.integration

import by.svyat.core.transaction.IntegrationTestBase
import by.svyat.core.transaction.entity.OutboxMessageEntity
import by.svyat.core.transaction.outbox.OutboxEventHandler
import by.svyat.core.transaction.outbox.configuration.OutboxProperties
import by.svyat.core.transaction.outbox.dto.OutboxEventMetadata
import by.svyat.core.transaction.outbox.enums.OutboxAggregateType
import by.svyat.core.transaction.outbox.impl.OutboxConsumerImpl
import by.svyat.core.transaction.repository.OutboxConsumerOffsetRepository
import by.svyat.core.transaction.repository.OutboxMessageRepository
import by.svyat.core.transaction.repository.OutboxPartitionLockRepository
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class OutboxConsumerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var messageRepository: OutboxMessageRepository

    @Autowired
    private lateinit var offsetRepository: OutboxConsumerOffsetRepository

    @Autowired
    private lateinit var lockRepository: OutboxPartitionLockRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private fun newConsumerGroup() = "test-${UUID.randomUUID()}"

    private fun createConsumer(
        consumerGroup: String,
        registry: Map<String, OutboxEventHandler>,
        partitionCount: Int = 4,
        batchSize: Int = 10
    ): OutboxConsumerImpl {
        val props = OutboxProperties(
            partitionCount = partitionCount,
            pollIntervalMs = 50,
            batchSize = batchSize,
            consumerGroup = consumerGroup,
            lockTtlSeconds = 30,
            consumerEnabled = true
        )
        return OutboxConsumerImpl(
            messageRepository, offsetRepository, lockRepository,
            props, transactionTemplate, registry, meterRegistry
        )
    }

    private fun saveMessage(
        aggregateType: String = OutboxAggregateType.TRANSACTION.name,
        aggregateId: String = "1",
        eventType: String = "TRANSFER_COMPLETED",
        partitionKey: String = "key",
        partitionNum: Int = 0,
        payload: String = """{"transactionId":1}"""
    ): OutboxMessageEntity {
        return messageRepository.save(
            OutboxMessageEntity(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                partitionKey = partitionKey,
                partitionNum = partitionNum,
                payload = payload
            )
        )
    }

    private fun collectingHandler(): Pair<CopyOnWriteArrayList<OutboxEventMetadata>, OutboxEventHandler> {
        val events = CopyOnWriteArrayList<OutboxEventMetadata>()
        val handler = object : OutboxEventHandler {
            override fun supportedAggregateTypes() = listOf(OutboxAggregateType.TRANSACTION)
            override fun handle(eventType: String, payload: String, metadata: OutboxEventMetadata) {
                events.add(metadata)
            }
        }
        return events to handler
    }

    private fun awaitCondition(timeoutMs: Long = 3000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue(condition()) { "Condition not met within ${timeoutMs}ms" }
    }

    @Nested
    inner class MessageProcessing {

        @Test
        fun `consumer processes messages from database`() {
            val (events, handler) = collectingHandler()
            val group = newConsumerGroup()

            saveMessage(partitionNum = 0, aggregateId = "10", payload = """{"tx":10}""")
            saveMessage(partitionNum = 0, aggregateId = "20", payload = """{"tx":20}""")

            val consumer = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to handler))
            consumer.start()
            try {
                awaitCondition { events.size >= 2 }
                assertTrue(events.any { it.aggregateId == "10" })
                assertTrue(events.any { it.aggregateId == "20" })
            } finally {
                consumer.stop()
            }
        }

        @Test
        fun `consumer processes messages from multiple partitions`() {
            val (events, handler) = collectingHandler()
            val group = newConsumerGroup()

            saveMessage(partitionNum = 0, aggregateId = "p0")
            saveMessage(partitionNum = 1, aggregateId = "p1")
            saveMessage(partitionNum = 2, aggregateId = "p2")
            saveMessage(partitionNum = 3, aggregateId = "p3")

            val consumer = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to handler))
            consumer.start()
            try {
                awaitCondition { events.size >= 4 }
                val ids = events.map { it.aggregateId }.toSet()
                assertTrue(ids.containsAll(setOf("p0", "p1", "p2", "p3")))
            } finally {
                consumer.stop()
            }
        }

        @Test
        fun `consumer passes correct event type and payload to handler`() {
            val payloads = CopyOnWriteArrayList<String>()
            val eventTypes = CopyOnWriteArrayList<String>()
            val handler = object : OutboxEventHandler {
                override fun supportedAggregateTypes() = listOf(OutboxAggregateType.TRANSACTION)
                override fun handle(eventType: String, payload: String, metadata: OutboxEventMetadata) {
                    eventTypes.add(eventType)
                    payloads.add(payload)
                }
            }
            val group = newConsumerGroup()

            saveMessage(partitionNum = 0, eventType = "BALANCE_CHANGED", payload = """{"amount":500}""")

            val consumer = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to handler))
            consumer.start()
            try {
                awaitCondition { payloads.isNotEmpty() }
                assertEquals("BALANCE_CHANGED", eventTypes[0])
                assertTrue(payloads[0].contains("500"))
            } finally {
                consumer.stop()
            }
        }

        @Test
        fun `consumer preserves message ordering within partition`() {
            val (events, handler) = collectingHandler()
            val group = newConsumerGroup()

            saveMessage(partitionNum = 0, aggregateId = "first")
            saveMessage(partitionNum = 0, aggregateId = "second")
            saveMessage(partitionNum = 0, aggregateId = "third")

            val consumer = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to handler))
            consumer.start()
            try {
                awaitCondition { events.size >= 3 }
                val partitionEvents = events.filter { it.partitionNum == 0 }
                assertTrue(partitionEvents.size >= 3)
                assertTrue(partitionEvents[0].messageId < partitionEvents[1].messageId)
                assertTrue(partitionEvents[1].messageId < partitionEvents[2].messageId)
            } finally {
                consumer.stop()
            }
        }
    }

    @Nested
    inner class OffsetTracking {

        @Test
        fun `consumer persists offset after processing`() {
            val (events, handler) = collectingHandler()
            val group = newConsumerGroup()

            saveMessage(partitionNum = 0, aggregateId = "1")

            val consumer = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to handler))
            consumer.start()
            try {
                awaitCondition { events.isNotEmpty() }
                // Даём время на коммит офсета
                Thread.sleep(200)
            } finally {
                consumer.stop()
            }

            val offset = offsetRepository.findByConsumerGroupAndPartitionNum(group, 0)
            assertNotNull(offset)
            assertTrue(offset!!.lastOffset > 0)
        }

        @Test
        fun `consumer resumes from last committed offset`() {
            val (events1, handler1) = collectingHandler()
            val group = newConsumerGroup()
            val registry = mapOf(OutboxAggregateType.TRANSACTION.name to handler1)

            saveMessage(partitionNum = 0, aggregateId = "old-1")
            saveMessage(partitionNum = 0, aggregateId = "old-2")

            val consumer1 = createConsumer(group, registry)
            consumer1.start()
            try {
                awaitCondition { events1.size >= 2 }
                Thread.sleep(200)
            } finally {
                consumer1.stop()
            }

            // Вторая порция — новые сообщения
            val (events2, handler2) = collectingHandler()
            saveMessage(partitionNum = 0, aggregateId = "new-1")
            saveMessage(partitionNum = 0, aggregateId = "new-2")

            val consumer2 = createConsumer(
                group, mapOf(OutboxAggregateType.TRANSACTION.name to handler2)
            )
            consumer2.start()
            try {
                awaitCondition { events2.size >= 2 }
                val ids = events2.map { it.aggregateId }.toSet()
                assertTrue(ids.containsAll(setOf("new-1", "new-2")))
                // Старые сообщения не переобработаны
                assertFalse(ids.contains("old-1"))
                assertFalse(ids.contains("old-2"))
            } finally {
                consumer2.stop()
            }
        }

        @Test
        fun `consumer does not advance offset on handler failure`() {
            val group = newConsumerGroup()
            val processedIds = CopyOnWriteArrayList<String>()
            val failAlwaysOnMsg2 = object : OutboxEventHandler {
                override fun supportedAggregateTypes() = listOf(OutboxAggregateType.TRANSACTION)
                override fun handle(eventType: String, payload: String, metadata: OutboxEventMetadata) {
                    if (metadata.aggregateId == "msg-2") throw RuntimeException("simulated failure")
                    processedIds.add(metadata.aggregateId)
                }
            }

            val msg1 = saveMessage(partitionNum = 0, aggregateId = "msg-1")
            saveMessage(partitionNum = 0, aggregateId = "msg-2")

            val consumer = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to failAlwaysOnMsg2))
            consumer.start()
            try {
                awaitCondition { processedIds.isNotEmpty() }
                Thread.sleep(300)
            } finally {
                consumer.stop()
            }

            val offset = offsetRepository.findByConsumerGroupAndPartitionNum(group, 0)
            assertNotNull(offset)
            assertEquals(msg1.id, offset!!.lastOffset)
        }
    }

    @Nested
    inner class PartitionLocking {

        @Test
        fun `two consumers with same group do not duplicate processing`() {
            val group = newConsumerGroup()

            repeat(10) { i ->
                saveMessage(partitionNum = 0, aggregateId = "msg-$i")
            }

            val events1 = CopyOnWriteArrayList<OutboxEventMetadata>()
            val events2 = CopyOnWriteArrayList<OutboxEventMetadata>()

            val handler1 = object : OutboxEventHandler {
                override fun supportedAggregateTypes() = listOf(OutboxAggregateType.TRANSACTION)
                override fun handle(eventType: String, payload: String, metadata: OutboxEventMetadata) {
                    events1.add(metadata)
                }
            }
            val handler2 = object : OutboxEventHandler {
                override fun supportedAggregateTypes() = listOf(OutboxAggregateType.TRANSACTION)
                override fun handle(eventType: String, payload: String, metadata: OutboxEventMetadata) {
                    events2.add(metadata)
                }
            }

            val consumer1 = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to handler1))
            val consumer2 = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to handler2))

            consumer1.start()
            consumer2.start()
            try {
                awaitCondition(5000) { events1.size + events2.size >= 10 }
                val allIds = (events1 + events2).map { it.messageId }.toSet()
                assertEquals(10, allIds.size, "All 10 messages should be processed without duplicates")
            } finally {
                consumer1.stop()
                consumer2.stop()
            }
        }

        @Test
        fun `consumer releases locks on stop`() {
            val group = newConsumerGroup()
            val (events, handler) = collectingHandler()

            saveMessage(partitionNum = 0, aggregateId = "1")

            val consumer = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to handler))
            consumer.start()
            try {
                awaitCondition { events.isNotEmpty() }
            } finally {
                consumer.stop()
            }

            val locks = lockRepository.findAll().filter { it.consumerGroup == group }
            assertTrue(locks.isEmpty()) { "Expected no locks after stop, found ${locks.size}" }
        }
    }

    @Nested
    inner class HandlerRouting {

        @Test
        fun `consumer routes messages to correct handler by aggregate type`() {
            val group = newConsumerGroup()
            val txEvents = CopyOnWriteArrayList<OutboxEventMetadata>()
            val accEvents = CopyOnWriteArrayList<OutboxEventMetadata>()

            val txHandler = object : OutboxEventHandler {
                override fun supportedAggregateTypes() = listOf(OutboxAggregateType.TRANSACTION)
                override fun handle(eventType: String, payload: String, metadata: OutboxEventMetadata) {
                    txEvents.add(metadata)
                }
            }
            val accHandler = object : OutboxEventHandler {
                override fun supportedAggregateTypes() = listOf(OutboxAggregateType.ACCOUNT)
                override fun handle(eventType: String, payload: String, metadata: OutboxEventMetadata) {
                    accEvents.add(metadata)
                }
            }

            saveMessage(partitionNum = 0, aggregateType = OutboxAggregateType.TRANSACTION.name, aggregateId = "tx-1")
            saveMessage(partitionNum = 0, aggregateType = OutboxAggregateType.ACCOUNT.name, aggregateId = "acc-1")

            val registry = mapOf(
                OutboxAggregateType.TRANSACTION.name to txHandler,
                OutboxAggregateType.ACCOUNT.name to accHandler
            )
            val consumer = createConsumer(group, registry)
            consumer.start()
            try {
                awaitCondition { txEvents.size >= 1 && accEvents.size >= 1 }
                assertEquals("tx-1", txEvents[0].aggregateId)
                assertEquals("acc-1", accEvents[0].aggregateId)
            } finally {
                consumer.stop()
            }
        }

        @Test
        fun `consumer skips messages with unregistered aggregate type`() {
            val group = newConsumerGroup()
            val (events, handler) = collectingHandler()

            saveMessage(partitionNum = 0, aggregateType = "UNKNOWN_TYPE", aggregateId = "skip-me")
            saveMessage(partitionNum = 0, aggregateType = OutboxAggregateType.TRANSACTION.name, aggregateId = "process-me")

            val consumer = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to handler))
            consumer.start()
            try {
                awaitCondition { events.isNotEmpty() }
                Thread.sleep(300)
                assertTrue(events.all { it.aggregateId == "process-me" })
            } finally {
                consumer.stop()
            }
        }
    }

    @Nested
    inner class BatchProcessing {

        @Test
        fun `consumer processes messages in batches across multiple poll cycles`() {
            val group = newConsumerGroup()
            val (events, handler) = collectingHandler()

            repeat(25) { i ->
                saveMessage(partitionNum = 0, aggregateId = "batch-$i")
            }

            val consumer = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to handler), batchSize = 10)
            consumer.start()
            try {
                awaitCondition(5000) { events.size >= 25 }
                val ids = events.map { it.aggregateId }.toSet()
                assertEquals(25, ids.size)
            } finally {
                consumer.stop()
            }
        }

        @Test
        fun `consumer polls new messages arriving after startup`() {
            val group = newConsumerGroup()
            val (events, handler) = collectingHandler()

            val consumer = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to handler))
            consumer.start()
            try {
                Thread.sleep(200)
                saveMessage(partitionNum = 0, aggregateId = "late-msg")

                awaitCondition { events.any { it.aggregateId == "late-msg" } }
            } finally {
                consumer.stop()
            }
        }
    }

    @Nested
    inner class ConsumerLifecycle {

        @Test
        fun `consumer is running after start and stopped after stop`() {
            val group = newConsumerGroup()
            val (_, handler) = collectingHandler()
            val consumer = createConsumer(group, mapOf(OutboxAggregateType.TRANSACTION.name to handler))

            assertFalse(consumer.isRunning)
            consumer.start()
            assertTrue(consumer.isRunning)
            consumer.stop()
            assertFalse(consumer.isRunning)
        }

        @Test
        fun `consumer does not start when disabled`() {
            val props = OutboxProperties(
                partitionCount = 4,
                pollIntervalMs = 50,
                batchSize = 10,
                consumerGroup = newConsumerGroup(),
                lockTtlSeconds = 30,
                consumerEnabled = false
            )
            val (_, handler) = collectingHandler()
            val consumer = OutboxConsumerImpl(
                messageRepository, offsetRepository, lockRepository,
                props, transactionTemplate, mapOf(OutboxAggregateType.TRANSACTION.name to handler), meterRegistry
            )

            consumer.start()
            assertFalse(consumer.isRunning)
        }
    }
}
