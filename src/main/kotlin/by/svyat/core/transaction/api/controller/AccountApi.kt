package by.svyat.core.transaction.api.controller

import by.svyat.core.transaction.api.dto.request.CreateAccountRequest
import by.svyat.core.transaction.api.dto.response.AccountResponse
import by.svyat.core.transaction.api.dto.response.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Tag(name = "Счета", description = "Управление банковскими счетами")
@RequestMapping("/api/v1/accounts")
interface AccountApi {

    @Operation(summary = "Создать счёт", description = "Открывает новый банковский счёт для пользователя")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Счёт создан"),
        ApiResponse(
            responseCode = "400", description = "Ошибка валидации",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "404", description = "Пользователь не найден",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping
    fun createAccount(@Valid @RequestBody request: CreateAccountRequest): ResponseEntity<AccountResponse>

    @Operation(summary = "Получить счёт по ID")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Счёт найден"),
        ApiResponse(
            responseCode = "404", description = "Счёт не найден",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @GetMapping("/{id}")
    fun getAccount(
        @Parameter(description = "ID счёта") @PathVariable id: Long
    ): ResponseEntity<AccountResponse>

    @Operation(summary = "Получить все счета пользователя")
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Список счетов",
            content = [Content(array = ArraySchema(schema = Schema(implementation = AccountResponse::class)))]
        )
    )
    @GetMapping("/user/{userId}")
    fun getAccountsByUser(
        @Parameter(description = "ID пользователя") @PathVariable userId: Long
    ): ResponseEntity<List<AccountResponse>>
}
