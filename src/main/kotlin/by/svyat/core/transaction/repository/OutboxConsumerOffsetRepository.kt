package by.svyat.core.transaction.repository

import by.svyat.core.transaction.entity.OutboxConsumerOffsetEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OutboxConsumerOffsetRepository : JpaRepository<OutboxConsumerOffsetEntity, Long> {

    fun findByConsumerGroupAndPartitionNum(consumerGroup: String, partitionNum: Int): OutboxConsumerOffsetEntity?

    @Modifying
    @Query(
        """
        INSERT INTO outbox_consumer_offsets (consumer_group, partition_num, last_offset, updated_at)
        VALUES (:group, :partition, :offset, NOW())
        ON CONFLICT (consumer_group, partition_num)
        DO UPDATE SET last_offset = :offset, updated_at = NOW()
        """,
        nativeQuery = true
    )
    fun upsertOffset(
        @Param("group") consumerGroup: String,
        @Param("partition") partitionNum: Int,
        @Param("offset") lastOffset: Long
    )
}
