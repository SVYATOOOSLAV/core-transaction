package by.svyat.core.transaction.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Core Transaction API")
                .description("Микросервис банковских транзакций — управление пользователями, счетами и операциями")
                .version("1.0.0")
                .contact(
                    Contact()
                        .name("Svyat")
                )
        )
        .servers(
            listOf(
                Server().url("http://localhost:8081").description("Local")
            )
        )
}
