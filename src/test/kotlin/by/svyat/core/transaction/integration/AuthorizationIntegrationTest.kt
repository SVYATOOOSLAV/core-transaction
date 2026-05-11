package by.svyat.core.transaction.integration

import by.svyat.core.transaction.IntegrationTestBase
import by.svyat.core.transaction.TestApiClient
import by.svyat.core.transaction.TestDataFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import java.math.BigDecimal

class AuthorizationIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var api: TestApiClient

    @Test
    fun `SUPPORT token can call gift`() {
        api.setAuthToken(authToken)
        val accounts = api.createUserWithCheckingAndSavings()

        val supportToken = jwtService.generateToken("test-support", listOf("SUPPORT"))
        val request = TestDataFactory.moneyGiftRequest(
            accounts.checkingAccountNumber, BigDecimal("100.00"), "Компенсация"
        )

        mockMvc.post("/api/v1/transactions/gift") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", "Bearer $supportToken")
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `SUPPORT token cannot call transfer to savings`() {
        api.setAuthToken(authToken)
        val accounts = api.createUserWithCheckingAndSavings()
        api.fundAccount(accounts.checkingAccountNumber)

        val supportToken = jwtService.generateToken("test-support", listOf("SUPPORT"))
        val request = TestDataFactory.transferRequest(
            accounts.checkingAccountNumber, accounts.savingsAccountNumber,
            amount = BigDecimal("100.00"), description = "test"
        )

        mockMvc.post("/api/v1/transactions/savings") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", "Bearer $supportToken")
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `no token returns 401`() {
        val request = TestDataFactory.moneyGiftRequest(
            "10000000000000000001", BigDecimal("100.00"), "x"
        )

        mockMvc.post("/api/v1/transactions/gift") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `invalid token returns 401`() {
        val request = TestDataFactory.moneyGiftRequest(
            "10000000000000000001", BigDecimal("100.00"), "x"
        )

        mockMvc.post("/api/v1/transactions/gift") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", "Bearer not-a-valid-jwt")
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
