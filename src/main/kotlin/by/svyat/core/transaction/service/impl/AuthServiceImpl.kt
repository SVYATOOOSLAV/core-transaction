package by.svyat.core.transaction.service.impl

import by.svyat.core.transaction.api.common.BusinessException
import by.svyat.core.transaction.api.dto.request.ServiceTokenRequest
import by.svyat.core.transaction.api.dto.response.AuthResponse
import by.svyat.core.transaction.configuration.ServiceAccountsProperties
import by.svyat.core.transaction.security.JwtService
import by.svyat.core.transaction.service.AuthService
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class AuthServiceImpl(
    private val jwtService: JwtService,
    private val properties: ServiceAccountsProperties
) : AuthService {

    override fun issueServiceToken(request: ServiceTokenRequest): AuthResponse {
        val account = properties.serviceAccounts.firstOrNull { it.name == request.name }
            ?: throw BusinessException(HttpStatus.UNAUTHORIZED, "Invalid service credentials")

        if (account.secret != request.secret) {
            throw BusinessException(HttpStatus.UNAUTHORIZED, "Invalid service credentials")
        }

        val token = jwtService.generateToken(account.name, account.roles)
        log.info { "Issued service token: name=${account.name}, roles=${account.roles}" }
        return AuthResponse(token = token)
    }
}
