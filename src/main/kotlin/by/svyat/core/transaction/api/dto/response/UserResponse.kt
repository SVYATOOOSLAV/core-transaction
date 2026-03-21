package by.svyat.core.transaction.api.dto.response

import java.time.OffsetDateTime

data class UserResponse(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val patronymic: String?,
    val phoneNumber: String,
    val email: String?,
    val createdAt: OffsetDateTime
)
