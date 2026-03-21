package by.svyat.core.transaction.api.dto.response

import java.math.BigDecimal
import java.time.OffsetDateTime

data class AccountResponse(
    val id: Long,
    val userId: Long,
    val accountNumber: String,
    val accountType: String,
    val currency: String,
    val balance: BigDecimal,
    val isActive: Boolean,
    val createdAt: OffsetDateTime
)
