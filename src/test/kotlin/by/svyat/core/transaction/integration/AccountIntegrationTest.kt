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
        fun `full lifecycle - create and get by id and user`() {
            val request = TestDataFactory.accountRequest(userId)

            val createResult = mockMvc.post("/api/v1/accounts") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.accountNumber") { value("40817810000000000001") }
                jsonPath("$.accountType") { value("CHECKING") }
                jsonPath("$.currency") { value("RUB") }
                jsonPath("$.balance") { value(0) }
                jsonPath("$.isActive") { value(true) }
            }.andReturn()

            val accountId = objectMapper.readTree(createResult.response.contentAsString)["id"].asLong()

            mockMvc.get("/api/v1/accounts/$accountId").andExpect {
                status { isOk() }
                jsonPath("$.id") { value(accountId) }
            }

            mockMvc.get("/api/v1/accounts/user/$userId").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
            }
        }

        @Test
        fun `duplicate number returns 409`() {
            api.createAccount(userId, accountNumber = "40817810000000000099")

            mockMvc.post("/api/v1/accounts") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    TestDataFactory.accountRequest(userId, accountNumber = "40817810000000000099")
                )
            }.andExpect { status { isConflict() } }
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
            listOf("CHECKING", "SAVINGS", "DEPOSIT", "BROKERAGE").forEachIndexed { i, type ->
                api.createAccount(userId, accountNumber = "4081781000000000000${i + 1}", accountType = type)
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
            mockMvc.get("/api/v1/accounts/999999").andExpect {
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
