package by.svyat.core.transaction.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import by.svyat.core.transaction.api.common.BusinessException
import by.svyat.core.transaction.api.controller.impl.UserController
import by.svyat.core.transaction.api.dto.request.CreateUserRequest
import by.svyat.core.transaction.api.dto.response.UserResponse
import by.svyat.core.transaction.service.UserService
import java.time.OffsetDateTime

@WebMvcTest(UserController::class)
class UserControllerTest {

    @TestConfiguration
    class Config {
        @Bean
        fun userService(): UserService = mockk()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userService: UserService

    private val now = OffsetDateTime.now()

    private fun userResponse() = UserResponse(
        id = 1L,
        firstName = "Иван",
        lastName = "Иванов",
        patronymic = "Иванович",
        phoneNumber = "+79991234567",
        email = "ivan@mail.ru",
        createdAt = now
    )

    @Test
    fun `POST - createUser - returns 201`() {
        val expected = userResponse()
        every {
            userService.createUser("Иван", "Иванов", "Иванович", "+79991234567", "ivan@mail.ru")
        } returns expected

        val request = CreateUserRequest("Иван", "Иванов", "Иванович", "+79991234567", "ivan@mail.ru")

        mockMvc.post("/api/v1/users") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(1) }
            jsonPath("$.firstName") { value("Иван") }
            jsonPath("$.phoneNumber") { value("+79991234567") }
        }
    }

    @Test
    fun `POST - createUser - validation error returns 400`() {
        val request = mapOf("firstName" to "", "lastName" to "Иванов", "phoneNumber" to "+79991234567")

        mockMvc.post("/api/v1/users") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST - createUser - duplicate phone returns 409`() {
        every {
            userService.createUser(any(), any(), any(), any(), any())
        } throws BusinessException(HttpStatus.CONFLICT, "User with phone number +79991234567 already exists")

        val request = CreateUserRequest("Иван", "Иванов", null, "+79991234567", null)

        mockMvc.post("/api/v1/users") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.message") { value("User with phone number +79991234567 already exists") }
        }
    }

    @Test
    fun `GET - getUser - returns 200`() {
        every { userService.getUser(1L) } returns userResponse()

        mockMvc.get("/api/v1/users/1").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(1) }
            jsonPath("$.lastName") { value("Иванов") }
        }
    }

    @Test
    fun `GET - getUser - not found returns 404`() {
        every { userService.getUser(99L) } throws BusinessException(HttpStatus.NOT_FOUND, "User with id 99 not found")

        mockMvc.get("/api/v1/users/99").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET - getUserByPhone - returns 200`() {
        every { userService.getUserByPhone("+79991234567") } returns userResponse()

        mockMvc.get("/api/v1/users/phone/+79991234567").andExpect {
            status { isOk() }
            jsonPath("$.phoneNumber") { value("+79991234567") }
        }
    }

    @Test
    fun `GET - getUserByPhone - not found returns 404`() {
        every {
            userService.getUserByPhone("+70000000000")
        } throws BusinessException(HttpStatus.NOT_FOUND, "User with phone number +70000000000 not found")

        mockMvc.get("/api/v1/users/phone/+70000000000").andExpect {
            status { isNotFound() }
        }
    }
}
