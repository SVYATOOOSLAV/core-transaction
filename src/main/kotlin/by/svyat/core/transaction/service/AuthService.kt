package by.svyat.core.transaction.service

import by.svyat.core.transaction.api.dto.request.ServiceTokenRequest
import by.svyat.core.transaction.api.dto.response.AuthResponse

interface AuthService {
    fun issueServiceToken(request: ServiceTokenRequest): AuthResponse
}
