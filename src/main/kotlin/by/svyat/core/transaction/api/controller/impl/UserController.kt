package by.svyat.core.transaction.api.controller.impl

import by.svyat.core.transaction.api.controller.UserApi
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import by.svyat.core.transaction.api.dto.request.CreateUserRequest
import by.svyat.core.transaction.api.dto.response.UserResponse
import by.svyat.core.transaction.service.UserService

@RestController
class UserController(
    private val userService: UserService
) : UserApi {

    override fun createUser(request: CreateUserRequest): ResponseEntity<UserResponse> {
        val response = userService.createUser(
            firstName = request.firstName,
            lastName = request.lastName,
            patronymic = request.patronymic,
            phoneNumber = request.phoneNumber,
            email = request.email
        )
        return ResponseEntity(response, HttpStatus.CREATED)
    }

    override fun getUser(id: Long): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.getUser(id))
    }

    override fun getUserByPhone(phoneNumber: String): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.getUserByPhone(phoneNumber))
    }
}
