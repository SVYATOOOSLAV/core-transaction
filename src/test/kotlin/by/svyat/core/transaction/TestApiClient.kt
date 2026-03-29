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
        accountType: String = "CHECKING",
        currency: String = "RUB"
    ): String {
        val request = TestDataFactory.accountRequest(userId, accountType, currency)
        val result = postJson("/api/v1/accounts", request)
        return extractAccountNumber(result)
    }

    fun createAccountWithCard(
        userId: Long,
        accountType: String = "CHECKING",
        currency: String = "RUB"
    ): AccountWithCard {
        val request = TestDataFactory.accountRequest(userId, accountType, currency)
        val result = postJson("/api/v1/accounts", request)
        val tree = objectMapper.readTree(result.response.contentAsString)
        return AccountWithCard(
            accountNumber = tree["accountNumber"].asText(),
            cardNumber = tree["cardNumber"]?.asText()
        )
    }

    fun fundAccount(accountNumber: String, amount: BigDecimal = BigDecimal("10000.00")): Long {
        val request = TestDataFactory.moneyGiftRequest(accountNumber, amount, "Начальное пополнение")
        val result = postJson("/api/v1/transactions/gift", request)
        return extractId(result)
    }

    fun createCard(
        accountNumber: String,
        cardNumber: String,
        expiryDate: LocalDate = LocalDate.now().plusYears(3)
    ): Long {
        val account = accountRepository.findByAccountNumber(accountNumber)
            ?: throw IllegalArgumentException("Account $accountNumber not found")
        val card = cardRepository.save(
            CardEntity(account = account, cardNumber = cardNumber, expiryDate = expiryDate)
        )
        return card.id
    }

    fun createUserWithCheckingAndSavings(
        phoneNumber: String = "+79991234567"
    ): TestAccounts {
        val userId = createUser(phoneNumber = phoneNumber)
        val checking = createAccountWithCard(userId, "CHECKING")
        val savingsNumber = createAccount(userId, "SAVINGS")
        return TestAccounts(userId, checking.accountNumber, savingsNumber, checking.cardNumber)
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

    private fun extractAccountNumber(result: MvcResult): String {
        return objectMapper.readTree(result.response.contentAsString)["accountNumber"].asText()
    }

    data class AccountWithCard(
        val accountNumber: String,
        val cardNumber: String?
    )

    data class TestAccounts(
        val userId: Long,
        val checkingAccountNumber: String,
        val savingsAccountNumber: String,
        val checkingCardNumber: String?
    )
}
