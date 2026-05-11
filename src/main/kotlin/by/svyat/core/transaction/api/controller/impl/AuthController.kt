package by.svyat.core.transaction.api.controller.impl

import by.svyat.core.transaction.api.controller.AuthApi
import by.svyat.core.transaction.api.dto.request.ServiceTokenRequest
import by.svyat.core.transaction.api.dto.response.AuthResponse
import by.svyat.core.transaction.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val authService: AuthService
) : AuthApi {

    override fun issueServiceToken(request: ServiceTokenRequest): ResponseEntity<AuthResponse> {
        return ResponseEntity.ok(authService.issueServiceToken(request))
    }
}
