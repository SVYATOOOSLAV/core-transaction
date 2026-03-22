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
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.net.InetAddress
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val log = KotlinLogging.logger {}

@Service
class OutboxConsumerImpl(
    private val outboxMessageRepository: OutboxMessageRepository,
    private val offsetRepository: OutboxConsumerOffsetRepository,
    private val lockRepository: OutboxPartitionLockRepository,
    private val outboxProperties: OutboxProperties,
    private val transactionTemplate: TransactionTemplate,
    private val outboxHandlerRegistry: Map<String, OutboxEventHandler>
) : OutboxConsumer {

    private val instanceId = "${InetAddress.getLocalHost().hostName}-${UUID.randomUUID()}"
    private lateinit var executor: ScheduledExecutorService
    private val running = AtomicBoolean(false)

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

        try {
            val pollResult = transactionTemplate.execute {
                val expiresAt = OffsetDateTime.now().plusSeconds(outboxProperties.lockTtlSeconds)
                val acquired = lockRepository.tryAcquireLock(
                    outboxProperties.consumerGroup, partition, instanceId, expiresAt
                )
                if (acquired == 0) return@execute null

                val currentOffset = offsetRepository
                    .findByConsumerGroupAndPartitionNum(outboxProperties.consumerGroup, partition)
                    ?.lastOffset ?: 0L

                val messages = outboxMessageRepository.findByPartitionAfterOffset(
                    partition, currentOffset, PageRequest.of(0, outboxProperties.batchSize)
                )
                if (messages.isEmpty()) return@execute null

                PollResult(currentOffset, messages)
            } ?: return

            var lastSuccessId = pollResult.currentOffset
            var processedCount = 0

            for (message in pollResult.messages) {
                val handler = outboxHandlerRegistry[message.aggregateType]
                if (handler == null) {
                    log.warn { "No handler for aggregateType=${message.aggregateType}, skipping messageId=${message.id}" }
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

                    handler.handle(message.eventType, message.payload, metadata)
                    lastSuccessId = message.id
                    processedCount++
                } catch (e: Exception) {
                    log.error(e) { "Handler failed on messageId=${message.id}, partition=$partition. Offset stays at $lastSuccessId" }
                    break
                }
            }

            if (lastSuccessId > pollResult.currentOffset) {
                offsetRepository.upsertOffset(outboxProperties.consumerGroup, partition, lastSuccessId)
                log.debug { "Processed $processedCount messages from partition=$partition, newOffset=$lastSuccessId" }
            }
        } catch (e: Exception) {
            log.error(e) { "Error polling partition=$partition" }
        }
    }
}
