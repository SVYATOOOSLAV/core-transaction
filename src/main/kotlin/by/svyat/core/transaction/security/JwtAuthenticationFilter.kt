package by.svyat.core.transaction.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private val log = KotlinLogging.logger {}

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractToken(request)

        if (token != null && jwtService.isTokenValid(token)) {
            try {
                val serviceName = jwtService.extractServiceName(token)
                val roles = jwtService.extractRoles(token)
                val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }
                val authentication = UsernamePasswordAuthenticationToken(serviceName, null, authorities)
                SecurityContextHolder.getContext().authentication = authentication
                log.debug { "Authenticated service=$serviceName roles=$roles" }
            } catch (e: Exception) {
                log.warn { "Failed to authenticate JWT: ${e.message}" }
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7)
        }
        return null
    }
}
