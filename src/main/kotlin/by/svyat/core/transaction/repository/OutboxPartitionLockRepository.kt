package by.svyat.core.transaction.repository

import by.svyat.core.transaction.entity.OutboxPartitionLockEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface OutboxPartitionLockRepository : JpaRepository<OutboxPartitionLockEntity, Long> {

    @Modifying
    @Query(
        """
        INSERT INTO outbox_partition_locks (consumer_group, partition_num, locked_by, locked_at, expires_at)
        VALUES (:group, :partition, :lockedBy, NOW(), :expiresAt)
        ON CONFLICT (consumer_group, partition_num)
        DO UPDATE SET locked_by = :lockedBy, locked_at = NOW(), expires_at = :expiresAt
        WHERE outbox_partition_locks.expires_at < NOW()
           OR outbox_partition_locks.locked_by = :lockedBy
        """,
        nativeQuery = true
    )
    fun tryAcquireLock(
        @Param("group") consumerGroup: String,
        @Param("partition") partitionNum: Int,
        @Param("lockedBy") lockedBy: String,
        @Param("expiresAt") expiresAt: OffsetDateTime
    ): Int

    @Modifying
    @Query(
        """
        DELETE FROM outbox_partition_locks
        WHERE consumer_group = :group
          AND partition_num = :partition
          AND locked_by = :lockedBy
        """,
        nativeQuery = true
    )
    fun releaseLock(
        @Param("group") consumerGroup: String,
        @Param("partition") partitionNum: Int,
        @Param("lockedBy") lockedBy: String
    )

    @Modifying
    @Query("DELETE FROM outbox_partition_locks WHERE expires_at < NOW()", nativeQuery = true)
    fun deleteExpiredLocks(): Int
}
