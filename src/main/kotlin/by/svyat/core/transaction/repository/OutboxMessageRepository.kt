package by.svyat.core.transaction.repository

import by.svyat.core.transaction.entity.OutboxMessageEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface OutboxMessageRepository : JpaRepository<OutboxMessageEntity, Long> {

    @Query(
        """
        SELECT m FROM OutboxMessageEntity m
        WHERE m.partitionNum = :partition
          AND m.id > :afterOffset
        ORDER BY m.id ASC
        """
    )
    fun findByPartitionAfterOffset(
        @Param("partition") partition: Int,
        @Param("afterOffset") afterOffset: Long,
        pageable: Pageable
    ): List<OutboxMessageEntity>

    @Modifying
    @Query(
        "DELETE FROM outbox_messages WHERE created_at < :before",
        nativeQuery = true
    )
    fun deleteOlderThan(@Param("before") before: OffsetDateTime): Int
}
