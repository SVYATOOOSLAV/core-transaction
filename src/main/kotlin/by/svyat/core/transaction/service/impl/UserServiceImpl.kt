package by.svyat.core.transaction.service.impl

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import by.svyat.core.transaction.api.common.BusinessException
import by.svyat.core.transaction.api.dto.response.UserResponse
import by.svyat.core.transaction.entity.UserEntity
import by.svyat.core.transaction.mapping.UserMapper
import by.svyat.core.transaction.repository.UserRepository
import by.svyat.core.transaction.service.UserService

private val log = KotlinLogging.logger {}

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val userMapper: UserMapper
) : UserService {

    @Transactional
    override fun createUser(
        firstName: String,
        lastName: String,
        patronymic: String?,
        phoneNumber: String,
        email: String?
    ): UserResponse {
        log.info { "Creating user: phone=$phoneNumber" }

        userRepository.findByPhoneNumber(phoneNumber)?.let {
            log.warn { "Duplicate phone number: $phoneNumber" }
            throw BusinessException(HttpStatus.CONFLICT, "User with phone number $phoneNumber already exists")
        }

        val user = UserEntity(
            firstName = firstName,
            lastName = lastName,
            patronymic = patronymic,
            phoneNumber = phoneNumber,
            email = email
        )
        val saved = userRepository.save(user)
        log.info { "User created: id=${saved.id}, phone=$phoneNumber" }
        return userMapper.toResponse(saved)
    }

    @Transactional(readOnly = true)
    override fun getUser(id: Long): UserResponse {
        log.debug { "Fetching user by id=$id" }
        val user = userRepository.findById(id)
            .orElseThrow { BusinessException(HttpStatus.NOT_FOUND, "User with id $id not found") }
        return userMapper.toResponse(user)
    }

    @Transactional(readOnly = true)
    override fun getUserByPhone(phoneNumber: String): UserResponse {
        log.debug { "Fetching user by phone=$phoneNumber" }
        val user = userRepository.findByPhoneNumber(phoneNumber)
            ?: throw BusinessException(HttpStatus.NOT_FOUND, "User with phone number $phoneNumber not found")
        return userMapper.toResponse(user)
    }
}
