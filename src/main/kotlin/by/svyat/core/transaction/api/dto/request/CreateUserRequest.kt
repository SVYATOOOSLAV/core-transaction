package by.svyat.core.transaction.api.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateUserRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val firstName: String,

    @field:NotBlank
    @field:Size(max = 100)
    val lastName: String,

    @field:Size(max = 100)
    val patronymic: String? = null,

    @field:NotBlank
    @field:Size(max = 20)
    val phoneNumber: String,

    @field:Email
    @field:Size(max = 150)
    val email: String? = null
)
