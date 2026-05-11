package by.svyat.core.transaction.integration

import by.svyat.core.transaction.IntegrationTestBase
import by.svyat.core.transaction.TestApiClient
import by.svyat.core.transaction.TestDataFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class TransactionIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var api: TestApiClient

    private lateinit var checkingAccountNumber: String
    private lateinit var savingsAccountNumber: String
    private var checkingCardNumber: String? = null
    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        api.setAuthToken(authToken)
        val accounts = api.createUserWithCheckingAndSavings()
        userId = accounts.userId
        checkingAccountNumber = accounts.checkingAccountNumber
        savingsAccountNumber = accounts.savingsAccountNumber
        checkingCardNumber = accounts.checkingCardNumber
        api.fundAccount(checkingAccountNumber)
    }

    @Nested
    inner class TransferToSavings {

        @Test
        fun `success and balances updated`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber,
                amount = BigDecimal("3000.00"), description = "На накопления"
            )

            authPost("/api/v1/transactions/savings", request).andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("TRANSFER_SAVINGS") }
                jsonPath("$.status") { value("COMPLETED") }
                jsonPath("$.amount") { value(3000.0) }
            }

            authGet("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(7000.0) }
            }

            authGet("/api/v1/accounts/$savingsAccountNumber").andExpect {
                jsonPath("$.balance") { value(3000.0) }
            }
        }

        @Test
        fun `idempotency returns same response`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber, amount = BigDecimal("1000.00")
            )

            authPost("/api/v1/transactions/savings", request).andExpect { status { isCreated() } }

            authPost("/api/v1/transactions/savings", request).andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("TRANSFER_SAVINGS") }
            }

            authGet("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(9000.0) }
            }
        }

        @Test
        fun `insufficient funds returns 400`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber, amount = BigDecimal("999999.00")
            )

            authPost("/api/v1/transactions/savings", request).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { exists() }
            }
        }

        @Test
        fun `both checking accounts returns 400`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, checkingAccountNumber, amount = BigDecimal("100.00")
            )

            authPost("/api/v1/transactions/savings", request).andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `backward direction (savings to checking) success`() {
            authPost("/api/v1/transactions/savings", TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber, amount = BigDecimal("4000.00")
            )).andExpect { status { isCreated() } }

            val backward = TestDataFactory.transferRequest(
                savingsAccountNumber, checkingAccountNumber,
                amount = BigDecimal("1500.00"), description = "Возврат на расчётный"
            )

            authPost("/api/v1/transactions/savings", backward).andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("TRANSFER_SAVINGS") }
                jsonPath("$.sourceAccountNumber") { value(savingsAccountNumber) }
                jsonPath("$.destinationAccountNumber") { value(checkingAccountNumber) }
            }

            authGet("/api/v1/accounts/$savingsAccountNumber").andExpect {
                jsonPath("$.balance") { value(2500.0) }
            }
            authGet("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(7500.0) }
            }
        }
    }

    @Nested
    inner class TransferToDeposit {

        @Test
        fun `success and balances updated`() {
            val depositAccountNumber = api.createAccount(userId, "DEPOSIT")

            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, depositAccountNumber,
                amount = BigDecimal("2000.00"), description = "На вклад"
            )

            authPost("/api/v1/transactions/deposit", request).andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("TRANSFER_DEPOSIT") }
                jsonPath("$.status") { value("COMPLETED") }
            }

            authGet("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(8000.0) }
            }

            authGet("/api/v1/accounts/$depositAccountNumber").andExpect {
                jsonPath("$.balance") { value(2000.0) }
            }
        }
    }

    @Nested
    inner class TransferToBrokerage {

        @Test
        fun `success and balances updated`() {
            val brokerageAccountNumber = api.createAccount(userId, "BROKERAGE")

            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, brokerageAccountNumber,
                amount = BigDecimal("5000.00"), description = "На брокерский счёт"
            )

            authPost("/api/v1/transactions/brokerage", request).andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("TRANSFER_BROKERAGE") }
                jsonPath("$.status") { value("COMPLETED") }
            }

            authGet("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(5000.0) }
            }

            authGet("/api/v1/accounts/$brokerageAccountNumber").andExpect {
                jsonPath("$.balance") { value(5000.0) }
            }
        }
    }

    @Nested
    inner class TransferChecking {

        @Test
        fun `success and balances updated`() {
            val secondCheckingNumber = api.createAccount(userId, "CHECKING")

            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, secondCheckingNumber,
                amount = BigDecimal("2500.00"), description = "Между расчётными"
            )

            authPost("/api/v1/transactions/checking", request).andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("TRANSFER_CHECKING") }
                jsonPath("$.status") { value("COMPLETED") }
                jsonPath("$.amount") { value(2500.0) }
            }

            authGet("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(7500.0) }
            }

            authGet("/api/v1/accounts/$secondCheckingNumber").andExpect {
                jsonPath("$.balance") { value(2500.0) }
            }
        }

        @Test
        fun `same source and destination returns 400`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, checkingAccountNumber, amount = BigDecimal("100.00")
            )

            authPost("/api/v1/transactions/checking", request).andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `non-checking destination returns 400`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber, amount = BigDecimal("100.00")
            )

            authPost("/api/v1/transactions/checking", request).andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class MoneyGift {

        @Test
        fun `credits destination account`() {
            val request = TestDataFactory.moneyGiftRequest(
                savingsAccountNumber, amount = BigDecimal("5000.00"), description = "Подарок"
            )

            authPost("/api/v1/transactions/gift", request).andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("MONEY_GIFT") }
                jsonPath("$.sourceAccountNumber") { doesNotExist() }
                jsonPath("$.destinationAccountNumber") { value(savingsAccountNumber) }
            }

            authGet("/api/v1/accounts/$savingsAccountNumber").andExpect {
                jsonPath("$.balance") { value(5000.0) }
            }
        }
    }

    @Nested
    inner class SbpTransfer {

        @Test
        fun `success and balances updated`() {
            val recipientUserId = api.createUser(phoneNumber = "+79997654321")
            val recipientAccountNumber = api.createAccount(recipientUserId, "CHECKING")

            val request = TestDataFactory.sbpTransferRequest(
                checkingAccountNumber, "+79997654321", amount = BigDecimal("2000.00"), description = "Перевод по СБП"
            )

            authPost("/api/v1/transactions/sbp", request).andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("SBP_TRANSFER") }
                jsonPath("$.status") { value("COMPLETED") }
                jsonPath("$.amount") { value(2000.0) }
            }

            authGet("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(8000.0) }
            }

            authGet("/api/v1/accounts/$recipientAccountNumber").andExpect {
                jsonPath("$.balance") { value(2000.0) }
            }
        }

        @Test
        fun `recipient not found returns 404`() {
            val request = TestDataFactory.sbpTransferRequest(
                checkingAccountNumber, "+70000000000"
            )

            authPost("/api/v1/transactions/sbp", request).andExpect {
                status { isNotFound() }
            }
        }

        @Test
        fun `insufficient funds returns 400`() {
            val recipientUserId = api.createUser(phoneNumber = "+79997654321")
            api.createAccount(recipientUserId, "CHECKING")

            val request = TestDataFactory.sbpTransferRequest(
                checkingAccountNumber, "+79997654321", amount = BigDecimal("999999.00")
            )

            authPost("/api/v1/transactions/sbp", request).andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class InterbankTransfer {

        @Test
        fun `success and balances updated`() {
            val sourceCardNumber = checkingCardNumber!!

            val recipientUserId = api.createUser(phoneNumber = "+79997654321")
            val recipient = api.createAccountWithCard(recipientUserId, "CHECKING")
            val recipientCardNumber = recipient.cardNumber!!

            val request = TestDataFactory.interbankTransferRequest(
                sourceCardNumber, recipientCardNumber,
                amount = BigDecimal("1500.00"), description = "Межбанковский перевод"
            )

            authPost("/api/v1/transactions/interbank", request).andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("INTERBANK_TRANSFER") }
                jsonPath("$.status") { value("COMPLETED") }
                jsonPath("$.amount") { value(1500.0) }
            }

            authGet("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(8500.0) }
            }

            authGet("/api/v1/accounts/${recipient.accountNumber}").andExpect {
                jsonPath("$.balance") { value(1500.0) }
            }
        }

        @Test
        fun `source card not found returns 404`() {
            val request = TestDataFactory.interbankTransferRequest(
                "9999999999999999", checkingCardNumber!!
            )

            authPost("/api/v1/transactions/interbank", request).andExpect {
                status { isNotFound() }
            }
        }

        @Test
        fun `insufficient funds returns 400`() {
            val recipientUserId = api.createUser(phoneNumber = "+79997654321")
            val recipient = api.createAccountWithCard(recipientUserId, "CHECKING")

            val request = TestDataFactory.interbankTransferRequest(
                checkingCardNumber!!, recipient.cardNumber!!, amount = BigDecimal("999999.00")
            )

            authPost("/api/v1/transactions/interbank", request).andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class GetTransaction {

        @Test
        fun `success`() {
            val request = TestDataFactory.moneyGiftRequest(
                checkingAccountNumber, amount = BigDecimal("100.00"), description = "test"
            )

            val result = authPost("/api/v1/transactions/gift", request).andReturn()

            val txId = objectMapper.readTree(result.response.contentAsString)["id"].asLong()

            authGet("/api/v1/transactions/$txId").andExpect {
                status { isOk() }
                jsonPath("$.id") { value(txId) }
                jsonPath("$.status") { value("COMPLETED") }
            }
        }

        @Test
        fun `not found returns 404`() {
            authGet("/api/v1/transactions/999999").andExpect {
                status { isNotFound() }
            }
        }

        @Test
        fun `by account - returns related transactions`() {
            repeat(3) {
                api.fundAccount(checkingAccountNumber, BigDecimal("100.00"))
            }

            authGet("/api/v1/transactions/account/$checkingAccountNumber").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(4) }
            }
        }
    }
}
