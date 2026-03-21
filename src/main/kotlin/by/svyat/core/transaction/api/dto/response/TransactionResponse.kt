package by.svyat.core.transaction.api.dto.response

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class TransactionResponse(
    val id: Long,
    val idempotencyKey: UUID,
    val transactionType: String,
    val status: String,
    val sourceAccountId: Long?,
    val destinationAccountId: Long?,
    val amount: BigDecimal,
    val currency: String,
    val description: String?,
    val errorMessage: String?,
    val createdAt: OffsetDateTime,
    val completedAt: OffsetDateTime?
)
