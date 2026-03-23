package by.svyat.core.transaction.outbox.impl

import by.svyat.core.transaction.entity.OutboxConsumerOffsetEntity
import by.svyat.core.transaction.entity.OutboxMessageEntity
import by.svyat.core.transaction.outbox.OutboxEventHandler
import by.svyat.core.transaction.outbox.configuration.OutboxProperties
import by.svyat.core.transaction.outbox.dto.OutboxEventMetadata
import by.svyat.core.transaction.outbox.enums.OutboxAggregateType
import by.svyat.core.transaction.repository.OutboxConsumerOffsetRepository
import by.svyat.core.transaction.repository.OutboxMessageRepository
import by.svyat.core.transaction.repository.OutboxPartitionLockRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class OutboxConsumerImplTest {

    private val messageRepository: OutboxMessageRepository = mockk()
    private val offsetRepository: OutboxConsumerOffsetRepository = mockk()
    private val lockRepository: OutboxPartitionLockRepository = mockk(relaxed = true)
    private val transactionTemplate: TransactionTemplate = mockk()
    private val handler: OutboxEventHandler = mockk(relaxed = true)
    private val meterRegistry: MeterRegistry = SimpleMeterRegistry()

    private val properties = OutboxProperties(
        partitionCount = 2,
        pollIntervalMs = 100,
        batchSize = 10,
        consumerGroup = "test-group",
        lockTtlSeconds = 30,
        consumerEnabled = false
    )

    private val handlerRegistry: Map<String, OutboxEventHandler> = mapOf(
        OutboxAggregateType.TRANSACTION.name to handler
    )

    private lateinit var consumer: OutboxConsumerImpl

    @BeforeEach
    fun setUp() {
        consumer = OutboxConsumerImpl(
            messageRepository, offsetRepository, lockRepository,
            properties, transactionTemplate, handlerRegistry, meterRegistry
        )
        every { transactionTemplate.execute<Any?>(any()) } answers {
            firstArg<TransactionCallback<Any?>>().doInTransaction(mockk())
        }
        every { transactionTemplate.executeWithoutResult(any()) } answers {
            firstArg<Consumer<TransactionStatus>>().accept(mockk())
        }
    }

    @Nested
    inner class Lifecycle {

        @Test
        fun `isAutoStartup returns consumerEnabled value`() {
            assertFalse(consumer.isAutoStartup)

            val enabledConsumer = OutboxConsumerImpl(
                messageRepository, offsetRepository, lockRepository,
                properties.copy(consumerEnabled = true), transactionTemplate, handlerRegistry, meterRegistry
            )
            assertTrue(enabledConsumer.isAutoStartup)
        }

        @Test
        fun `isRunning returns false before start`() {
            assertFalse(consumer.isRunning)
        }

        @Test
        fun `start does nothing when consumer is disabled`() {
            consumer.start()
            assertFalse(consumer.isRunning)
        }

        @Test
        fun `start sets running to true when enabled`() {
            val enabledConsumer = OutboxConsumerImpl(
                messageRepository, offsetRepository, lockRepository,
                properties.copy(consumerEnabled = true), transactionTemplate, handlerRegistry, meterRegistry
            )
            enabledConsumer.start()
            assertTrue(enabledConsumer.isRunning)
            enabledConsumer.stop()
        }

        @Test
        fun `stop sets running to false and releases locks`() {
            val enabledConsumer = OutboxConsumerImpl(
                messageRepository, offsetRepository, lockRepository,
                properties.copy(consumerEnabled = true), transactionTemplate, handlerRegistry, meterRegistry
            )
            enabledConsumer.start()
            enabledConsumer.stop()

            assertFalse(enabledConsumer.isRunning)
            verify(exactly = 2) {
                lockRepository.releaseLock("test-group", any(), any())
            }
        }

        @Test
        fun `stop is idempotent when not running`() {
            consumer.stop()
            assertFalse(consumer.isRunning)
        }

        @Test
        fun `getPhase returns MAX_VALUE minus 1`() {
            assertEquals(Int.MAX_VALUE - 1, consumer.phase)
        }
    }

    @Nested
    inner class PollPartition {

        private fun createMessage(
            id: Long,
            aggregateType: String = OutboxAggregateType.TRANSACTION.name,
            eventType: String = "TRANSFER_COMPLETED",
            payload: String = """{"transactionId":$id}""",
            partition: Int = 0
        ) = OutboxMessageEntity(
            id = id,
            aggregateType = aggregateType,
            aggregateId = id.toString(),
            eventType = eventType,
            partitionKey = "key-$id",
            partitionNum = partition,
            payload = payload,
            createdAt = OffsetDateTime.now()
        )

        @Test
        fun `processes messages and updates offset`() {
            every { lockRepository.tryAcquireLock(any(), eq(0), any(), any()) } returns 1
            every {
                offsetRepository.findByConsumerGroupAndPartitionNum("test-group", 0)
            } returns null
            every {
                messageRepository.findByPartitionAfterOffset(0, 0L, PageRequest.of(0, 10))
            } returns listOf(createMessage(1), createMessage(2), createMessage(3))
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            invokePollPartition(0)

            verify(exactly = 3) { handler.handle(any(), any(), any()) }
            verify { offsetRepository.upsertOffset("test-group", 0, 3L) }
        }

        @Test
        fun `does not poll when lock is not acquired`() {
            every { lockRepository.tryAcquireLock(any(), eq(0), any(), any()) } returns 0

            invokePollPartition(0)

            verify(exactly = 0) { messageRepository.findByPartitionAfterOffset(any(), any(), any()) }
            verify(exactly = 0) { handler.handle(any(), any(), any()) }
        }

        @Test
        fun `uses existing offset from repository`() {
            val existingOffset = OutboxConsumerOffsetEntity(
                id = 1, consumerGroup = "test-group", partitionNum = 0,
                lastOffset = 50L, updatedAt = OffsetDateTime.now()
            )
            every { lockRepository.tryAcquireLock(any(), eq(0), any(), any()) } returns 1
            every {
                offsetRepository.findByConsumerGroupAndPartitionNum("test-group", 0)
            } returns existingOffset
            every {
                messageRepository.findByPartitionAfterOffset(0, 50L, PageRequest.of(0, 10))
            } returns listOf(createMessage(51))
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            invokePollPartition(0)

            verify { messageRepository.findByPartitionAfterOffset(0, 50L, any()) }
            verify { offsetRepository.upsertOffset("test-group", 0, 51L) }
        }

        @Test
        fun `defaults offset to 0 when no offset exists`() {
            every { lockRepository.tryAcquireLock(any(), eq(0), any(), any()) } returns 1
            every {
                offsetRepository.findByConsumerGroupAndPartitionNum("test-group", 0)
            } returns null
            every {
                messageRepository.findByPartitionAfterOffset(0, 0L, PageRequest.of(0, 10))
            } returns emptyList()

            invokePollPartition(0)

            verify { messageRepository.findByPartitionAfterOffset(0, 0L, any()) }
        }

        @Test
        fun `does not update offset when no messages found`() {
            every { lockRepository.tryAcquireLock(any(), eq(0), any(), any()) } returns 1
            every {
                offsetRepository.findByConsumerGroupAndPartitionNum("test-group", 0)
            } returns null
            every {
                messageRepository.findByPartitionAfterOffset(0, 0L, PageRequest.of(0, 10))
            } returns emptyList()

            invokePollPartition(0)

            verify(exactly = 0) { offsetRepository.upsertOffset(any(), any(), any()) }
        }

        @Test
        fun `skips message with unknown aggregate type and advances offset`() {
            every { lockRepository.tryAcquireLock(any(), eq(0), any(), any()) } returns 1
            every {
                offsetRepository.findByConsumerGroupAndPartitionNum("test-group", 0)
            } returns null
            every {
                messageRepository.findByPartitionAfterOffset(0, 0L, PageRequest.of(0, 10))
            } returns listOf(createMessage(1, aggregateType = "UNKNOWN"), createMessage(2))
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            invokePollPartition(0)

            verify(exactly = 1) { handler.handle(any(), any(), any()) }
            verify { offsetRepository.upsertOffset("test-group", 0, 2L) }
        }

        @Test
        fun `stops processing on handler exception and preserves offset at last success`() {
            every { lockRepository.tryAcquireLock(any(), eq(0), any(), any()) } returns 1
            every {
                offsetRepository.findByConsumerGroupAndPartitionNum("test-group", 0)
            } returns null
            every {
                messageRepository.findByPartitionAfterOffset(0, 0L, PageRequest.of(0, 10))
            } returns listOf(createMessage(1), createMessage(2), createMessage(3))
            every { handler.handle(any(), any(), match { it.messageId == 1L }) } just Runs
            every { handler.handle(any(), any(), match { it.messageId == 2L }) } throws RuntimeException("handler error")
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            invokePollPartition(0)

            verify { offsetRepository.upsertOffset("test-group", 0, 1L) }
            verify(exactly = 0) { handler.handle(any(), any(), match { it.messageId == 3L }) }
        }

        @Test
        fun `does not update offset when first message fails`() {
            every { lockRepository.tryAcquireLock(any(), eq(0), any(), any()) } returns 1
            every {
                offsetRepository.findByConsumerGroupAndPartitionNum("test-group", 0)
            } returns null
            every {
                messageRepository.findByPartitionAfterOffset(0, 0L, PageRequest.of(0, 10))
            } returns listOf(createMessage(1))
            every { handler.handle(any(), any(), any()) } throws RuntimeException("fail")

            invokePollPartition(0)

            verify(exactly = 0) { offsetRepository.upsertOffset(any(), any(), any()) }
        }

        @Test
        fun `passes correct metadata to handler`() {
            val message = createMessage(
                id = 42,
                aggregateType = OutboxAggregateType.TRANSACTION.name,
                eventType = "BALANCE_CHANGED",
                payload = """{"amount":100}""",
                partition = 0
            )
            every { lockRepository.tryAcquireLock(any(), eq(0), any(), any()) } returns 1
            every {
                offsetRepository.findByConsumerGroupAndPartitionNum("test-group", 0)
            } returns null
            every {
                messageRepository.findByPartitionAfterOffset(0, 0L, PageRequest.of(0, 10))
            } returns listOf(message)
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            val metadataSlot = slot<OutboxEventMetadata>()
            val eventTypeSlot = slot<String>()
            val payloadSlot = slot<String>()
            every { handler.handle(capture(eventTypeSlot), capture(payloadSlot), capture(metadataSlot)) } just Runs

            invokePollPartition(0)

            assertEquals("BALANCE_CHANGED", eventTypeSlot.captured)
            assertEquals("""{"amount":100}""", payloadSlot.captured)
            with(metadataSlot.captured) {
                assertEquals(42L, messageId)
                assertEquals(OutboxAggregateType.TRANSACTION.name, aggregateType)
                assertEquals("42", aggregateId)
                assertEquals("key-42", partitionKey)
                assertEquals(0, partitionNum)
            }
        }

        @Test
        fun `processes batch up to configured batch size`() {
            val smallBatchProperties = properties.copy(batchSize = 3)
            val smallBatchConsumer = OutboxConsumerImpl(
                messageRepository, offsetRepository, lockRepository,
                smallBatchProperties, transactionTemplate, handlerRegistry, meterRegistry
            )

            every { lockRepository.tryAcquireLock(any(), eq(0), any(), any()) } returns 1
            every {
                offsetRepository.findByConsumerGroupAndPartitionNum("test-group", 0)
            } returns null
            every {
                messageRepository.findByPartitionAfterOffset(0, 0L, PageRequest.of(0, 3))
            } returns listOf(createMessage(1), createMessage(2), createMessage(3))
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            invokePrivatePollPartition(smallBatchConsumer, 0)

            verify { messageRepository.findByPartitionAfterOffset(0, 0L, PageRequest.of(0, 3)) }
        }

        @Test
        fun `handles multiple messages of different aggregate types`() {
            val accountHandler: OutboxEventHandler = mockk(relaxed = true)
            val multiRegistry = mapOf(
                OutboxAggregateType.TRANSACTION.name to handler,
                OutboxAggregateType.ACCOUNT.name to accountHandler
            )
            val multiConsumer = OutboxConsumerImpl(
                messageRepository, offsetRepository, lockRepository,
                properties, transactionTemplate, multiRegistry, meterRegistry
            )

            every { lockRepository.tryAcquireLock(any(), eq(0), any(), any()) } returns 1
            every {
                offsetRepository.findByConsumerGroupAndPartitionNum("test-group", 0)
            } returns null
            every {
                messageRepository.findByPartitionAfterOffset(0, 0L, PageRequest.of(0, 10))
            } returns listOf(
                createMessage(1, aggregateType = OutboxAggregateType.TRANSACTION.name),
                createMessage(2, aggregateType = OutboxAggregateType.ACCOUNT.name)
            )
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            invokePrivatePollPartition(multiConsumer, 0)

            verify(exactly = 1) { handler.handle(any(), any(), match { it.messageId == 1L }) }
            verify(exactly = 1) { accountHandler.handle(any(), any(), match { it.messageId == 2L }) }
            verify { offsetRepository.upsertOffset("test-group", 0, 2L) }
        }

        @Test
        fun `does not poll when consumer is not running`() {
            // running = false по умолчанию, pollPartition должен выйти сразу
            val method = OutboxConsumerImpl::class.java.getDeclaredMethod("pollPartition", Int::class.java)
            method.isAccessible = true
            method.invoke(consumer, 0)

            verify(exactly = 0) { lockRepository.tryAcquireLock(any(), any(), any(), any()) }
        }

        private fun invokePollPartition(partition: Int) {
            invokePrivatePollPartition(consumer, partition)
        }

        private fun invokePrivatePollPartition(target: OutboxConsumerImpl, partition: Int) {
            val runningField = OutboxConsumerImpl::class.java.getDeclaredField("running")
            runningField.isAccessible = true
            (runningField.get(target) as AtomicBoolean).set(true)

            val method = OutboxConsumerImpl::class.java.getDeclaredMethod("pollPartition", Int::class.java)
            method.isAccessible = true
            method.invoke(target, partition)
        }
    }

    @Nested
    inner class Metrics {

        private fun createMessage(
            id: Long,
            aggregateType: String = OutboxAggregateType.TRANSACTION.name,
            eventType: String = "TRANSFER_COMPLETED",
            payload: String = """{"transactionId":$id}""",
            partition: Int = 0,
            createdAt: OffsetDateTime = OffsetDateTime.now()
        ) = OutboxMessageEntity(
            id = id,
            aggregateType = aggregateType,
            aggregateId = id.toString(),
            eventType = eventType,
            partitionKey = "key-$id",
            partitionNum = partition,
            payload = payload,
            createdAt = createdAt
        )

        private fun setupLockAcquired(partition: Int = 0) {
            every { lockRepository.tryAcquireLock(any(), eq(partition), any(), any()) } returns 1
        }

        private fun setupNoOffset(partition: Int = 0) {
            every {
                offsetRepository.findByConsumerGroupAndPartitionNum("test-group", partition)
            } returns null
        }

        private fun setupMessages(partition: Int = 0, messages: List<OutboxMessageEntity>) {
            every {
                messageRepository.findByPartitionAfterOffset(partition, 0L, PageRequest.of(0, 10))
            } returns messages
        }

        private fun invokePoll(partition: Int = 0) {
            val runningField = OutboxConsumerImpl::class.java.getDeclaredField("running")
            runningField.isAccessible = true
            (runningField.get(consumer) as AtomicBoolean).set(true)

            val method = OutboxConsumerImpl::class.java.getDeclaredMethod("pollPartition", Int::class.java)
            method.isAccessible = true
            method.invoke(consumer, partition)
        }

        @Test
        fun `increments processed counter with status success on successful handling`() {
            setupLockAcquired()
            setupNoOffset()
            setupMessages(messages = listOf(createMessage(1), createMessage(2)))
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            invokePoll()

            val counter = meterRegistry.find("outbox.consumer.processed")
                .tag("aggregateType", "TRANSACTION")
                .tag("eventType", "TRANSFER_COMPLETED")
                .tag("status", "success")
                .counter()

            assertEquals(2.0, counter?.count())
        }

        @Test
        fun `increments processed counter with status error on handler failure`() {
            setupLockAcquired()
            setupNoOffset()
            setupMessages(messages = listOf(createMessage(1)))
            every { handler.handle(any(), any(), any()) } throws RuntimeException("fail")

            invokePoll()

            val counter = meterRegistry.find("outbox.consumer.processed")
                .tag("status", "error")
                .counter()

            assertEquals(1.0, counter?.count())
        }

        @Test
        fun `increments processed counter with status skipped for unknown aggregateType`() {
            setupLockAcquired()
            setupNoOffset()
            setupMessages(messages = listOf(createMessage(1, aggregateType = "UNKNOWN")))
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            invokePoll()

            val counter = meterRegistry.find("outbox.consumer.processed")
                .tag("aggregateType", "UNKNOWN")
                .tag("status", "skipped")
                .counter()

            assertEquals(1.0, counter?.count())
        }

        @Test
        fun `records process duration for each message`() {
            setupLockAcquired()
            setupNoOffset()
            setupMessages(messages = listOf(createMessage(1), createMessage(2)))
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            invokePoll()

            val timer = meterRegistry.find("outbox.consumer.process.duration")
                .tag("aggregateType", "TRANSACTION")
                .tag("eventType", "TRANSFER_COMPLETED")
                .timer()

            assertEquals(2L, timer?.count())
        }

        @Test
        fun `records processing lag`() {
            setupLockAcquired()
            setupNoOffset()
            val pastTime = OffsetDateTime.now().minusSeconds(5)
            setupMessages(messages = listOf(createMessage(1, createdAt = pastTime)))
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            invokePoll()

            val timer = meterRegistry.find("outbox.consumer.lag")
                .tag("partition", "0")
                .timer()

            assertNotNull(timer)
            assertEquals(1L, timer!!.count())
            assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.SECONDS) >= 4.0) {
                "Lag should be at least 4 seconds, was ${timer.totalTime(java.util.concurrent.TimeUnit.SECONDS)}"
            }
        }

        @Test
        fun `records poll duration`() {
            setupLockAcquired()
            setupNoOffset()
            setupMessages(messages = listOf(createMessage(1)))
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            invokePoll()

            val timer = meterRegistry.find("outbox.consumer.poll.duration")
                .tag("partition", "0")
                .timer()

            assertNotNull(timer)
            assertEquals(1L, timer!!.count())
        }

        @Test
        fun `records batch size`() {
            setupLockAcquired()
            setupNoOffset()
            setupMessages(messages = listOf(createMessage(1), createMessage(2), createMessage(3)))
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            invokePoll()

            val summary = meterRegistry.find("outbox.consumer.poll.batch.size")
                .tag("partition", "0")
                .summary()

            assertNotNull(summary)
            assertEquals(1L, summary!!.count())
            assertEquals(3.0, summary.totalAmount())
        }

        @Test
        fun `increments lock acquired counter with result success`() {
            setupLockAcquired()
            setupNoOffset()
            every {
                messageRepository.findByPartitionAfterOffset(0, 0L, PageRequest.of(0, 10))
            } returns emptyList()

            invokePoll()

            val counter = meterRegistry.find("outbox.consumer.lock.acquired")
                .tag("partition", "0")
                .tag("result", "success")
                .counter()

            assertEquals(1.0, counter?.count())
        }

        @Test
        fun `increments lock acquired counter with result failure`() {
            every { lockRepository.tryAcquireLock(any(), eq(0), any(), any()) } returns 0

            invokePoll()

            val counter = meterRegistry.find("outbox.consumer.lock.acquired")
                .tag("partition", "0")
                .tag("result", "failure")
                .counter()

            assertEquals(1.0, counter?.count())
        }

        @Test
        fun `increments errors counter on poll exception`() {
            every { transactionTemplate.execute<Any?>(any()) } throws RuntimeException("db error")

            invokePoll()

            val counter = meterRegistry.find("outbox.consumer.errors")
                .tag("partition", "0")
                .counter()

            assertEquals(1.0, counter?.count())
        }

        @Test
        fun `updates offset gauge after processing`() {
            setupLockAcquired()
            setupNoOffset()
            setupMessages(messages = listOf(createMessage(1), createMessage(2)))
            every { offsetRepository.upsertOffset(any(), any(), any()) } just Runs

            // Need to register gauges first via start, but consumer is disabled
            // offsetGauges are populated in pollPartition only if start() was called
            // For unit test we need to call start on enabled consumer
            val enabledRegistry = SimpleMeterRegistry()
            val enabledConsumer = OutboxConsumerImpl(
                messageRepository, offsetRepository, lockRepository,
                properties.copy(consumerEnabled = true), transactionTemplate, handlerRegistry, enabledRegistry
            )
            enabledConsumer.start()

            // Now manually poll
            val method = OutboxConsumerImpl::class.java.getDeclaredMethod("pollPartition", Int::class.java)
            method.isAccessible = true
            method.invoke(enabledConsumer, 0)

            val gauge = enabledRegistry.find("outbox.consumer.offset")
                .tag("partition", "0")
                .gauge()

            assertNotNull(gauge)
            assertEquals(2.0, gauge!!.value())

            enabledConsumer.stop()
        }
    }
}
