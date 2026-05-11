package by.svyat.core.transaction.configuration

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig(
    @Value("\${server.port:8080}") private val serverPort: Int
) {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Core Transaction API")
                .description("Микросервис банковских транзакций — управление пользователями, счетами и операциями")
                .version("1.0.0")
                .contact(Contact().name("Svyat"))
        )
        .servers(listOf(Server().url("http://localhost:$serverPort").description("Local")))
        .components(
            Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                )
        )
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))

    @Bean
    fun authResponsesCustomizer(): GlobalOpenApiCustomizer = GlobalOpenApiCustomizer { openApi ->
        val errorContent = Content().addMediaType(
            org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
            MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/ErrorResponse"))
        )
        val unauthorized = ApiResponse()
            .description("Не аутентифицирован — отсутствует или невалидный JWT")
            .content(errorContent)
        val forbidden = ApiResponse()
            .description("Недостаточно прав (роль не позволяет вызвать этот endpoint)")
            .content(errorContent)

        openApi.paths?.values?.forEach { pathItem ->
            pathItem.readOperations().forEach { op ->
                val responses = op.responses ?: return@forEach
                if (!responses.containsKey("401")) responses.addApiResponse("401", unauthorized)
                if (!responses.containsKey("403")) responses.addApiResponse("403", forbidden)
            }
        }
    }
}
