package by.svyat.core.transaction.api.common

import org.springframework.http.HttpStatus

class BusinessException(
    val httpStatus: HttpStatus,
    override val message: String
) : RuntimeException(message)
