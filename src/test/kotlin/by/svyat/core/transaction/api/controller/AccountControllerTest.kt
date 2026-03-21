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
import by.svyat.core.transaction.api.controller.impl.AccountController
import by.svyat.core.transaction.api.dto.request.CreateAccountRequest
import by.svyat.core.transaction.api.dto.response.AccountResponse
import by.svyat.core.transaction.entity.enums.AccountType
import by.svyat.core.transaction.service.AccountService
import java.math.BigDecimal
import java.time.OffsetDateTime

@WebMvcTest(AccountController::class)
class AccountControllerTest {

    @TestConfiguration
    class Config {
        @Bean
        fun accountService(): AccountService = mockk()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var accountService: AccountService

    private val now = OffsetDateTime.now()

    private fun accountResponse(
        id: Long = 1L,
        accountNumber: String = "1000000000000000001",
        accountType: String = "CHECKING"
    ) = AccountResponse(
        id = id,
        userId = 1L,
        accountNumber = accountNumber,
        accountType = accountType,
        currency = "RUB",
        balance = BigDecimal.ZERO,
        isActive = true,
        createdAt = now
    )

    @Test
    fun `POST - createAccount - returns 201`() {
        every {
            accountService.createAccount(1L, AccountType.CHECKING, "RUB")
        } returns accountResponse()

        val request = CreateAccountRequest(1L, "CHECKING", "RUB")

        mockMvc.post("/api/v1/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(1) }
            jsonPath("$.accountType") { value("CHECKING") }
            jsonPath("$.accountNumber") { value("1000000000000000001") }
        }
    }

    @Test
    fun `POST - createAccount - invalid account type returns 400`() {
        val request = CreateAccountRequest(1L, "INVALID_TYPE", "RUB")

        mockMvc.post("/api/v1/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { exists() }
        }
    }

    @Test
    fun `POST - createAccount - lowercase account type is accepted`() {
        every {
            accountService.createAccount(1L, AccountType.SAVINGS, "RUB")
        } returns accountResponse(accountType = "SAVINGS", accountNumber = "2000000000000000001")

        val request = CreateAccountRequest(1L, "savings", "RUB")

        mockMvc.post("/api/v1/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.accountType") { value("SAVINGS") }
        }
    }

    @Test
    fun `POST - createAccount - user not found returns 404`() {
        every {
            accountService.createAccount(99L, any(), any())
        } throws BusinessException(HttpStatus.NOT_FOUND, "User with id 99 not found")

        val request = CreateAccountRequest(99L, "CHECKING", "RUB")

        mockMvc.post("/api/v1/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET - getAccount - returns 200`() {
        every { accountService.getAccount("1000000000000000001") } returns accountResponse()

        mockMvc.get("/api/v1/accounts/1000000000000000001").andExpect {
            status { isOk() }
            jsonPath("$.accountNumber") { value("1000000000000000001") }
        }
    }

    @Test
    fun `GET - getAccount - not found returns 404`() {
        every { accountService.getAccount("9999999999999999999") } throws BusinessException(HttpStatus.NOT_FOUND, "Account not found")

        mockMvc.get("/api/v1/accounts/9999999999999999999").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET - getAccountsByUser - returns list`() {
        every { accountService.getAccountsByUser(1L) } returns listOf(
            accountResponse(id = 1L),
            accountResponse(id = 2L, accountType = "SAVINGS", accountNumber = "2000000000000000001")
        )

        mockMvc.get("/api/v1/accounts/user/1").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].id") { value(1) }
            jsonPath("$[1].accountType") { value("SAVINGS") }
        }
    }

    @Test
    fun `GET - getAccountsByUser - returns empty list`() {
        every { accountService.getAccountsByUser(99L) } returns emptyList()

        mockMvc.get("/api/v1/accounts/user/99").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }
}
