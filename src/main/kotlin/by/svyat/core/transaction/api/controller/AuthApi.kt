package by.svyat.core.transaction.api.controller

import by.svyat.core.transaction.api.dto.request.ServiceTokenRequest
import by.svyat.core.transaction.api.dto.response.AuthResponse
import by.svyat.core.transaction.api.dto.response.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Tag(name = "Аутентификация", description = "Выдача JWT для сервис-аккаунтов")
@RequestMapping("/api/v1/auth")
interface AuthApi {

    @Operation(
        summary = "Выпуск service-token",
        description = "Возвращает JWT для сервис-аккаунта (gateway, support и т.п.) по имени и секрету"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Токен выдан"),
        ApiResponse(
            responseCode = "401", description = "Неверные учётные данные сервиса",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping("/service-token")
    fun issueServiceToken(@Valid @RequestBody request: ServiceTokenRequest): ResponseEntity<AuthResponse>
}
