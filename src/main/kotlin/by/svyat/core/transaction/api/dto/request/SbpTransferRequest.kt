package by.svyat.core.transaction.api.dto.request

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

data class SbpTransferRequest(
    @field:NotNull
    val idempotencyKey: UUID,

    @field:NotBlank
    val sourceAccountNumber: String,

    @field:NotBlank
    val recipientPhoneNumber: String,

    @field:NotNull
    @field:DecimalMin(value = "0.01")
    val amount: BigDecimal,

    val description: String? = null
)
