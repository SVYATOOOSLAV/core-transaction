package by.svyat.core.transaction.api.controller

import by.svyat.core.transaction.api.dto.request.CreateUserRequest
import by.svyat.core.transaction.api.dto.response.ErrorResponse
import by.svyat.core.transaction.api.dto.response.UserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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

@Tag(name = "Пользователи", description = "Управление пользователями")
@RequestMapping("/api/v1/users")
interface UserApi {

    @Operation(summary = "Создать пользователя", description = "Регистрирует нового пользователя в системе")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Пользователь создан"),
        ApiResponse(
            responseCode = "400", description = "Ошибка валидации",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409", description = "Пользователь с таким номером телефона уже существует",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping
    fun createUser(@Valid @RequestBody request: CreateUserRequest): ResponseEntity<UserResponse>

    @Operation(summary = "Получить пользователя по ID")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Пользователь найден"),
        ApiResponse(
            responseCode = "404", description = "Пользователь не найден",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @GetMapping("/{id}")
    fun getUser(
        @Parameter(description = "ID пользователя") @PathVariable id: Long
    ): ResponseEntity<UserResponse>

    @Operation(summary = "Получить пользователя по номеру телефона")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Пользователь найден"),
        ApiResponse(
            responseCode = "404", description = "Пользователь не найден",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @GetMapping("/phone/{phoneNumber}")
    fun getUserByPhone(
        @Parameter(description = "Номер телефона", example = "+79991234567") @PathVariable phoneNumber: String
    ): ResponseEntity<UserResponse>
}
