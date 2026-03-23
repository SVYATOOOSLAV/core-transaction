package by.svyat.core.transaction.outbox.impl

import by.svyat.core.transaction.entity.OutboxMessageEntity
import by.svyat.core.transaction.outbox.configuration.OutboxProperties
import by.svyat.core.transaction.outbox.OutboxConsumer
import by.svyat.core.transaction.outbox.OutboxEventHandler
import by.svyat.core.transaction.outbox.dto.OutboxEventMetadata
import by.svyat.core.transaction.outbox.entity.PollResult
import by.svyat.core.transaction.repository.OutboxConsumerOffsetRepository
import by.svyat.core.transaction.repository.OutboxMessageRepository
import by.svyat.core.transaction.repository.OutboxPartitionLockRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.net.InetAddress
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val log = KotlinLogging.logger {}

@Service
class OutboxConsumerImpl(
    private val outboxMessageRepository: OutboxMessageRepository,
    private val offsetRepository: OutboxConsumerOffsetRepository,
    private val lockRepository: OutboxPartitionLockRepository,
    private val outboxProperties: OutboxProperties,
    private val transactionTemplate: TransactionTemplate,
    private val outboxHandlerRegistry: Map<String, OutboxEventHandler>,
    private val meterRegistry: MeterRegistry
) : OutboxConsumer {

    private val instanceId = "${InetAddress.getLocalHost().hostName}-${UUID.randomUUID()}"
    private lateinit var executor: ScheduledExecutorService
    private val running = AtomicBoolean(false)
    private val offsetGauges = ConcurrentHashMap<Int, AtomicLong>()

    override fun isAutoStartup(): Boolean = outboxProperties.consumerEnabled

    override fun start() {
        if (!outboxProperties.consumerEnabled) {
            log.info { "Outbox consumer is disabled" }
            return
        }

        log.info { "Starting outbox consumer: instanceId=$instanceId, partitions=${outboxProperties.partitionCount}, handlers=${outboxHandlerRegistry.keys}" }
        running.set(true)
        executor = Executors.newScheduledThreadPool(outboxProperties.partitionCount)

        for (partition in 0 until outboxProperties.partitionCount) {
            val offsetValue = AtomicLong(0)
            offsetGauges[partition] = offsetValue
            meterRegistry.gauge(
                "outbox.consumer.offset",
                listOf(Tag.of("partition", partition.toString())),
                offsetValue
            ) { it.toDouble() }

            executor.scheduleWithFixedDelay(
                { pollPartition(partition) },
                0,
                outboxProperties.pollIntervalMs,
                TimeUnit.MILLISECONDS
            )
        }
    }

    override fun stop() {
        if (!running.get()) return

        log.info { "Stopping outbox consumer: instanceId=$instanceId" }
        running.set(false)
        executor.shutdown()

        try {
            executor.awaitTermination(10, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            executor.shutdownNow()
        }

        for (partition in 0 until outboxProperties.partitionCount) {
            try {
                transactionTemplate.executeWithoutResult {
                    lockRepository.releaseLock(outboxProperties.consumerGroup, partition, instanceId)
                }
            } catch (e: Exception) {
                log.warn(e) { "Failed to release lock for partition=$partition" }
            }
        }
    }

    override fun isRunning(): Boolean = running.get()

    override fun getPhase(): Int = Int.MAX_VALUE - 1

    private fun pollPartition(partition: Int) {
        if (!running.get()) return

        val pollSample = Timer.start(meterRegistry)

        try {
            val pollResult = transactionTemplate.execute {
                val expiresAt = OffsetDateTime.now().plusSeconds(outboxProperties.lockTtlSeconds)
                val acquired = lockRepository.tryAcquireLock(
                    outboxProperties.consumerGroup, partition, instanceId, expiresAt
                )

                lockCounter(partition, if (acquired > 0) "success" else "failure").increment()

                if (acquired == 0) return@execute null

                val currentOffset = offsetRepository
                    .findByConsumerGroupAndPartitionNum(outboxProperties.consumerGroup, partition)
                    ?.lastOffset ?: 0L

                val messages = outboxMessageRepository.findByPartitionAfterOffset(
                    partition, currentOffset, PageRequest.of(0, outboxProperties.batchSize)
                )

                batchSizeSummary(partition).record(messages.size.toDouble())

                if (messages.isEmpty()) return@execute null

                PollResult(currentOffset, messages)
            } ?: run {
                pollSample.stop(pollDurationTimer(partition))
                return
            }

            var lastSuccessId = pollResult.currentOffset
            var processedCount = 0

            for (message in pollResult.messages) {
                val handler = outboxHandlerRegistry[message.aggregateType]
                if (handler == null) {
                    log.warn { "No handler for aggregateType=${message.aggregateType}, skipping messageId=${message.id}" }
                    processedCounter(message.aggregateType, message.eventType, "skipped").increment()
                    lastSuccessId = message.id
                    processedCount++
                    continue
                }

                try {
                    val metadata = OutboxEventMetadata(
                        messageId = message.id,
                        aggregateType = message.aggregateType,
                        aggregateId = message.aggregateId,
                        partitionKey = message.partitionKey,
                        partitionNum = message.partitionNum,
                        createdAt = message.createdAt
                    )

                    val processSample = Timer.start(meterRegistry)
                    handler.handle(message.eventType, message.payload, metadata)
                    processSample.stop(processDurationTimer(message.aggregateType, message.eventType))

                    val lag = Duration.between(message.createdAt, OffsetDateTime.now())
                    lagTimer(partition).record(lag)

                    processedCounter(message.aggregateType, message.eventType, "success").increment()
                    lastSuccessId = message.id
                    processedCount++
                } catch (e: Exception) {
                    log.error(e) { "Handler failed on messageId=${message.id}, partition=$partition. Offset stays at $lastSuccessId" }
                    processedCounter(message.aggregateType, message.eventType, "error").increment()
                    break
                }
            }

            if (lastSuccessId > pollResult.currentOffset) {
                offsetRepository.upsertOffset(outboxProperties.consumerGroup, partition, lastSuccessId)
                offsetGauges[partition]?.set(lastSuccessId)
                log.debug { "Processed $processedCount messages from partition=$partition, newOffset=$lastSuccessId" }
            }
        } catch (e: Exception) {
            log.error(e) { "Error polling partition=$partition" }
            errorsCounter(partition).increment()
        }

        pollSample.stop(pollDurationTimer(partition))
    }

    private fun processedCounter(aggregateType: String, eventType: String, status: String) =
        Counter.builder("outbox.consumer.processed")
            .tag("aggregateType", aggregateType)
            .tag("eventType", eventType)
            .tag("status", status)
            .description("Total outbox messages processed by status")
            .register(meterRegistry)

    private fun processDurationTimer(aggregateType: String, eventType: String) =
        Timer.builder("outbox.consumer.process.duration")
            .tag("aggregateType", aggregateType)
            .tag("eventType", eventType)
            .description("Handler processing time per message")
            .register(meterRegistry)

    private fun lagTimer(partition: Int) =
        Timer.builder("outbox.consumer.lag")
            .tag("partition", partition.toString())
            .description("Lag between message creation and processing")
            .register(meterRegistry)

    private fun pollDurationTimer(partition: Int) =
        Timer.builder("outbox.consumer.poll.duration")
            .tag("partition", partition.toString())
            .description("Full poll cycle duration")
            .register(meterRegistry)

    private fun batchSizeSummary(partition: Int) =
        DistributionSummary.builder("outbox.consumer.poll.batch.size")
            .tag("partition", partition.toString())
            .description("Number of messages fetched per poll")
            .register(meterRegistry)

    private fun lockCounter(partition: Int, result: String) =
        Counter.builder("outbox.consumer.lock.acquired")
            .tag("partition", partition.toString())
            .tag("result", result)
            .description("Lock acquisition outcomes")
            .register(meterRegistry)

    private fun errorsCounter(partition: Int) =
        Counter.builder("outbox.consumer.errors")
            .tag("partition", partition.toString())
            .description("Unhandled errors in poll cycle")
            .register(meterRegistry)
}
