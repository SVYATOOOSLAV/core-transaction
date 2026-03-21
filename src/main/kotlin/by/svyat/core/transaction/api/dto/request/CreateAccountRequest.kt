package by.svyat.core.transaction.api.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateAccountRequest(
    @field:NotNull
    val userId: Long,

    @field:NotBlank
    val accountType: String,

    @field:NotBlank
    @field:Size(max = 3)
    val currency: String = "RUB"
)
