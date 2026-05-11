package by.svyat.core.transaction.api.dto.request

import jakarta.validation.constraints.NotBlank

data class ServiceTokenRequest(
    @field:NotBlank
    val name: String,

    @field:NotBlank
    val secret: String
)
