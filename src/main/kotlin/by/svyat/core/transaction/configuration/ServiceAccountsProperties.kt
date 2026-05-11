package by.svyat.core.transaction.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth")
data class ServiceAccountsProperties(
    val serviceAccounts: List<ServiceAccountConfig> = emptyList()
) {
    data class ServiceAccountConfig(
        val name: String,
        val roles: List<String>,
        val secret: String
    )
}
