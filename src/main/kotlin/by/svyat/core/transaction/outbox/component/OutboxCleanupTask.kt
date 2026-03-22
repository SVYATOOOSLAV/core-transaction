package by.svyat.core.transaction.outbox.component

import by.svyat.core.transaction.outbox.configuration.OutboxProperties
import by.svyat.core.transaction.repository.OutboxMessageRepository
import by.svyat.core.transaction.repository.OutboxPartitionLockRepository
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

private val log = KotlinLogging.logger {}

@Component
class OutboxCleanupTask(
    private val outboxMessageRepository: OutboxMessageRepository,
    private val lockRepository: OutboxPartitionLockRepository,
    private val outboxProperties: OutboxProperties
) {

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun cleanup() {
        val cutoff = OffsetDateTime.now().minusDays(outboxProperties.cleanupRetentionDays)
        val deletedMessages = outboxMessageRepository.deleteOlderThan(cutoff)
        val deletedLocks = lockRepository.deleteExpiredLocks()
        log.info { "Outbox cleanup: removed $deletedMessages old messages, $deletedLocks expired locks" }
    }
}