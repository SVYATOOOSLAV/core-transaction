package by.svyat.core.transaction

import by.svyat.core.transaction.entity.CardEntity
import by.svyat.core.transaction.repository.AccountRepository
import by.svyat.core.transaction.repository.CardRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDate

@Component
class TestApiClient(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val cardRepository: CardRepository,
    private val accountRepository: AccountRepository
) {

    fun createUser(
        firstName: String = "Иван",
        lastName: String = "Иванов",
        patronymic: String? = null,
        phoneNumber: String = "+79991234567",
        email: String? = null
    ): Long {
        val request = TestDataFactory.userRequest(firstName, lastName, patronymic, phoneNumber, email)
        val result = postJson("/api/v1/users", request)
        return extractId(result)
    }

    fun createAccount(
        userId: Long,
        accountNumber: String = "40817810000000000001",
        accountType: String = "CHECKING",
        currency: String = "RUB"
    ): Long {
        val request = TestDataFactory.accountRequest(userId, accountNumber, accountType, currency)
        val result = postJson("/api/v1/accounts", request)
        return extractId(result)
    }

    fun fundAccount(accountId: Long, amount: BigDecimal = BigDecimal("10000.00")): Long {
        val request = TestDataFactory.moneyGiftRequest(accountId, amount, "Начальное пополнение")
        val result = postJson("/api/v1/transactions/gift", request)
        return extractId(result)
    }

    fun createUserWithCheckingAndSavings(
        phoneNumber: String = "+79991234567"
    ): TestAccounts {
        val userId = createUser(phoneNumber = phoneNumber)
        val checkingId = createAccount(userId, "40817810000000000001", "CHECKING")
        val savingsId = createAccount(userId, "40817810000000000002", "SAVINGS")
        return TestAccounts(userId, checkingId, savingsId)
    }

    fun createCard(
        accountId: Long,
        cardNumber: String,
        expiryDate: LocalDate = LocalDate.now().plusYears(3)
    ): Long {
        val account = accountRepository.findById(accountId)
            .orElseThrow { IllegalArgumentException("Account $accountId not found") }
        val card = cardRepository.save(
            CardEntity(account = account, cardNumber = cardNumber, expiryDate = expiryDate)
        )
        return card.id
    }

    fun postJson(url: String, body: Any): MvcResult {
        return mockMvc.post(url) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andReturn()
    }

    private fun extractId(result: MvcResult): Long {
        return objectMapper.readTree(result.response.contentAsString)["id"].asLong()
    }

    data class TestAccounts(
        val userId: Long,
        val checkingAccountId: Long,
        val savingsAccountId: Long
    )
}
