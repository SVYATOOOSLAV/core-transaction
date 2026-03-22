package by.svyat.core.transaction.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "outbox_partition_locks")
class OutboxPartitionLockEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "consumer_group", nullable = false, length = 100)
    val consumerGroup: String,

    @Column(name = "partition_num", nullable = false)
    val partitionNum: Int,

    @Column(name = "locked_by", nullable = false, length = 200)
    val lockedBy: String,

    @Column(name = "locked_at", nullable = false)
    val lockedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: OffsetDateTime
)
