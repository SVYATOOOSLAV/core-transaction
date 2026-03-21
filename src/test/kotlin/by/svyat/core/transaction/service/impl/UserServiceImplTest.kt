package by.svyat.core.transaction.service.impl

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import by.svyat.core.transaction.api.common.BusinessException
import by.svyat.core.transaction.api.dto.response.UserResponse
import by.svyat.core.transaction.entity.UserEntity
import by.svyat.core.transaction.mapping.UserMapper
import by.svyat.core.transaction.repository.UserRepository
import java.time.OffsetDateTime
import java.util.*

class UserServiceImplTest {

    private val userRepository: UserRepository = mockk()
    private val userMapper: UserMapper = mockk()
    private val userService = UserServiceImpl(userRepository, userMapper)

    private val now = OffsetDateTime.now()

    private fun userEntity(
        id: Long = 1L,
        firstName: String = "Иван",
        lastName: String = "Иванов",
        patronymic: String? = "Иванович",
        phoneNumber: String = "+79991234567",
        email: String? = "ivan@mail.ru"
    ) = UserEntity(
        id = id,
        firstName = firstName,
        lastName = lastName,
        patronymic = patronymic,
        phoneNumber = phoneNumber,
        email = email
    )

    private fun userResponse(
        id: Long = 1L,
        firstName: String = "Иван",
        lastName: String = "Иванов",
        patronymic: String? = "Иванович",
        phoneNumber: String = "+79991234567",
        email: String? = "ivan@mail.ru"
    ) = UserResponse(
        id = id,
        firstName = firstName,
        lastName = lastName,
        patronymic = patronymic,
        phoneNumber = phoneNumber,
        email = email,
        createdAt = now
    )

    // ===== createUser =====

    @Test
    fun `createUser - success`() {
        val phone = "+79991234567"
        val expectedResponse = userResponse()

        every { userRepository.findByPhoneNumber(phone) } returns null
        every { userRepository.save(any()) } answers { firstArg() }
        every { userMapper.toResponse(any()) } returns expectedResponse

        val result = userService.createUser("Иван", "Иванов", "Иванович", phone, "ivan@mail.ru")

        assertEquals(expectedResponse, result)
        verify { userRepository.findByPhoneNumber(phone) }
        verify { userRepository.save(any()) }
        verify { userMapper.toResponse(any()) }
    }

    @Test
    fun `createUser - without optional fields`() {
        val phone = "+79991234567"
        val expectedResponse = userResponse(patronymic = null, email = null)

        every { userRepository.findByPhoneNumber(phone) } returns null
        every { userRepository.save(any()) } answers { firstArg() }
        every { userMapper.toResponse(any()) } returns expectedResponse

        val result = userService.createUser("Иван", "Иванов", null, phone, null)

        assertEquals(expectedResponse, result)
    }

    @Test
    fun `createUser - duplicate phone number throws CONFLICT`() {
        val phone = "+79991234567"
        val existingUser = userEntity(phoneNumber = phone)

        every { userRepository.findByPhoneNumber(phone) } returns existingUser

        val exception = assertThrows<BusinessException> {
            userService.createUser("Пётр", "Петров", null, phone, null)
        }

        assertEquals(HttpStatus.CONFLICT, exception.httpStatus)
        verify { userRepository.findByPhoneNumber(phone) }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    // ===== getUser =====

    @Test
    fun `getUser - success`() {
        val entity = userEntity()
        val expectedResponse = userResponse()

        every { userRepository.findById(1L) } returns Optional.of(entity)
        every { userMapper.toResponse(entity) } returns expectedResponse

        val result = userService.getUser(1L)

        assertEquals(expectedResponse, result)
        verify { userRepository.findById(1L) }
    }

    @Test
    fun `getUser - not found throws NOT_FOUND`() {
        every { userRepository.findById(99L) } returns Optional.empty()

        val exception = assertThrows<BusinessException> {
            userService.getUser(99L)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.httpStatus)
    }

    // ===== getUserByPhone =====

    @Test
    fun `getUserByPhone - success`() {
        val phone = "+79991234567"
        val entity = userEntity(phoneNumber = phone)
        val expectedResponse = userResponse(phoneNumber = phone)

        every { userRepository.findByPhoneNumber(phone) } returns entity
        every { userMapper.toResponse(entity) } returns expectedResponse

        val result = userService.getUserByPhone(phone)

        assertEquals(expectedResponse, result)
        verify { userRepository.findByPhoneNumber(phone) }
    }

    @Test
    fun `getUserByPhone - not found throws NOT_FOUND`() {
        val phone = "+70000000000"

        every { userRepository.findByPhoneNumber(phone) } returns null

        val exception = assertThrows<BusinessException> {
            userService.getUserByPhone(phone)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.httpStatus)
    }
}
