package by.svyat.core.transaction.integration

import by.svyat.core.transaction.IntegrationTestBase
import by.svyat.core.transaction.TestApiClient
import by.svyat.core.transaction.TestDataFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class AccountIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var api: TestApiClient

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        userId = api.createUser()
    }

    @Nested
    inner class CreateAccount {

        @Test
        fun `full lifecycle - create and get by accountNumber and user`() {
            val request = TestDataFactory.accountRequest(userId)

            val createResult = mockMvc.post("/api/v1/accounts") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.accountType") { value("CHECKING") }
                jsonPath("$.currency") { value("RUB") }
                jsonPath("$.balance") { value(0) }
                jsonPath("$.isActive") { value(true) }
                jsonPath("$.accountNumber") { exists() }
            }.andReturn()

            val accountNumber = objectMapper.readTree(createResult.response.contentAsString)["accountNumber"].asText()

            mockMvc.get("/api/v1/accounts/$accountNumber").andExpect {
                status { isOk() }
                jsonPath("$.accountNumber") { value(accountNumber) }
            }

            mockMvc.get("/api/v1/accounts/user/$userId").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
            }
        }

        @Test
        fun `generated account numbers have correct prefix`() {
            val checkingNumber = api.createAccount(userId, "CHECKING")
            val savingsNumber = api.createAccount(userId, "SAVINGS")
            val depositNumber = api.createAccount(userId, "DEPOSIT")
            val brokerageNumber = api.createAccount(userId, "BROKERAGE")

            assert(checkingNumber.startsWith("1")) { "CHECKING should start with 1, got $checkingNumber" }
            assert(savingsNumber.startsWith("2")) { "SAVINGS should start with 2, got $savingsNumber" }
            assert(depositNumber.startsWith("3")) { "DEPOSIT should start with 3, got $depositNumber" }
            assert(brokerageNumber.startsWith("4")) { "BROKERAGE should start with 4, got $brokerageNumber" }
        }

        @Test
        fun `user not found returns 404`() {
            mockMvc.post("/api/v1/accounts") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    TestDataFactory.accountRequest(userId = 999999L)
                )
            }.andExpect { status { isNotFound() } }
        }

        @Test
        fun `invalid account type returns 400`() {
            mockMvc.post("/api/v1/accounts") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    TestDataFactory.accountRequest(userId, accountType = "INVALID")
                )
            }.andExpect { status { isBadRequest() } }
        }

        @Test
        fun `multiple account types for same user`() {
            listOf("CHECKING", "SAVINGS", "DEPOSIT", "BROKERAGE").forEach { type ->
                api.createAccount(userId, accountType = type)
            }

            mockMvc.get("/api/v1/accounts/user/$userId").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(4) }
            }
        }
    }

    @Nested
    inner class GetAccount {

        @Test
        fun `not found returns 404`() {
            mockMvc.get("/api/v1/accounts/9999999999999999999").andExpect {
                status { isNotFound() }
            }
        }

        @Test
        fun `by user - empty list`() {
            mockMvc.get("/api/v1/accounts/user/$userId").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
        }
    }
}
