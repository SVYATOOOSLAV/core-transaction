package by.svyat.core.transaction.service

import by.svyat.core.transaction.api.dto.response.UserResponse

interface UserService {
    fun createUser(firstName: String, lastName: String, patronymic: String?, phoneNumber: String, email: String?): UserResponse
    fun getUser(id: Long): UserResponse
    fun getUserByPhone(phoneNumber: String): UserResponse
}
