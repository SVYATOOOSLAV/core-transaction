package by.svyat.core.transaction.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "outbox_consumer_offsets")
class OutboxConsumerOffsetEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "consumer_group", nullable = false, length = 100)
    val consumerGroup: String,

    @Column(name = "partition_num", nullable = false)
    val partitionNum: Int,

    @Column(name = "last_offset", nullable = false)
    var lastOffset: Long = 0,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
