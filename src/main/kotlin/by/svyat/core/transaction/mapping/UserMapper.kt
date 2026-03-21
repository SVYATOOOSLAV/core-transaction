package by.svyat.core.transaction.mapping

import org.springframework.stereotype.Component
import by.svyat.core.transaction.api.dto.response.UserResponse
import by.svyat.core.transaction.entity.UserEntity

@Component
class UserMapper {

    fun toResponse(entity: UserEntity): UserResponse {
        return UserResponse(
            id = entity.id,
            firstName = entity.firstName,
            lastName = entity.lastName,
            patronymic = entity.patronymic,
            phoneNumber = entity.phoneNumber,
            email = entity.email,
            createdAt = entity.createdAt
        )
    }
}
