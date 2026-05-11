package by.svyat.core.transaction

import by.svyat.core.transaction.configuration.JwtProperties
import by.svyat.core.transaction.security.JwtService
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@TestConfiguration
class TestSecurityConfig {

    @Bean
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }

    @Bean
    fun jwtProperties(): JwtProperties = JwtProperties(
        secret = "test-secret-key-for-jwt-must-be-at-least-256-bits-long-for-testing!",
        expirationMs = 86400000
    )

    @Bean
    fun jwtService(): JwtService = JwtService(jwtProperties())
}
