package by.svyat.core.transaction.repository

import org.springframework.data.jpa.repository.JpaRepository
import by.svyat.core.transaction.entity.TransactionEntity
import java.util.*

interface TransactionRepository : JpaRepository<TransactionEntity, Long> {

    fun findByIdempotencyKey(idempotencyKey: UUID): TransactionEntity?

    fun findAllBySourceAccountAccountNumberOrDestinationAccountAccountNumber(
        sourceAccountNumber: String,
        destinationAccountNumber: String
    ): List<TransactionEntity>
}
