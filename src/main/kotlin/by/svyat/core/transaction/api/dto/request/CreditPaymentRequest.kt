package by.svyat.core.transaction.api.dto.request

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

data class CreditPaymentRequest(
    @field:NotNull
    val idempotencyKey: UUID,

    @field:NotNull
    val destinationAccountId: Long,

    @field:NotNull
    @field:DecimalMin(value = "0.01")
    val amount: BigDecimal,

    val description: String? = null
)
