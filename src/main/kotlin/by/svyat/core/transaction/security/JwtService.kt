package by.svyat.core.transaction.security

import by.svyat.core.transaction.configuration.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(private val jwtProperties: JwtProperties) {

    private val key: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())

    fun generateToken(serviceName: String, roles: List<String>): String {
        require(roles.isNotEmpty()) { "Service account must have at least one role" }
        val now = Date()
        return Jwts.builder()
            .subject(serviceName)
            .claim(ROLES_CLAIM, roles)
            .issuedAt(now)
            .expiration(Date(now.time + jwtProperties.expirationMs))
            .signWith(key)
            .compact()
    }

    fun extractServiceName(token: String): String = extractClaims(token).subject

    @Suppress("UNCHECKED_CAST")
    fun extractRoles(token: String): List<String> {
        val claim = extractClaims(token)[ROLES_CLAIM]
            ?: throw IllegalArgumentException("JWT does not contain roles claim")
        return (claim as List<*>).map { it.toString() }
    }

    fun isTokenValid(token: String): Boolean {
        return try {
            val claims = extractClaims(token)
            claims.expiration.after(Date())
        } catch (e: Exception) {
            false
        }
    }

    private fun extractClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    companion object {
        private const val ROLES_CLAIM = "roles"
    }
}
