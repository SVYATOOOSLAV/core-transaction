package by.svyat.core.transaction.api.dto.outbox

import by.svyat.core.transaction.entity.enums.TransactionStatus
import by.svyat.core.transaction.entity.enums.TransactionType
import java.math.BigDecimal

data class TransactionOutboxPayload(
    val transactionId: Long,
    val type: TransactionType,
    val sourceAccountNumber: String?,
    val destinationAccountNumber: String,
    val amount: BigDecimal,
    val currency: String,
    val status: TransactionStatus
)
