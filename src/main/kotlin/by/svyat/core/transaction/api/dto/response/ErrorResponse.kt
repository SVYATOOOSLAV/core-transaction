package by.svyat.core.transaction.api.dto.response

import java.time.OffsetDateTime

data class ErrorResponse(
    val timestamp: OffsetDateTime,
    val status: Int,
    val error: String,
    val message: String?,
    val path: String?
)
