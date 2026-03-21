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
import java.math.BigDecimal

class TransactionIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var api: TestApiClient

    private lateinit var checkingAccountNumber: String
    private lateinit var savingsAccountNumber: String
    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        val accounts = api.createUserWithCheckingAndSavings()
        userId = accounts.userId
        checkingAccountNumber = accounts.checkingAccountNumber
        savingsAccountNumber = accounts.savingsAccountNumber
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

            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("TRANSFER_SAVINGS") }
                jsonPath("$.status") { value("COMPLETED") }
                jsonPath("$.amount") { value(3000.0) }
            }

            mockMvc.get("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(7000.0) }
            }

            mockMvc.get("/api/v1/accounts/$savingsAccountNumber").andExpect {
                jsonPath("$.balance") { value(3000.0) }
            }
        }

        @Test
        fun `idempotency returns same response`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber, amount = BigDecimal("1000.00")
            )

            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect { status { isCreated() } }

            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("TRANSFER_SAVINGS") }
            }

            mockMvc.get("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(9000.0) }
            }
        }

        @Test
        fun `insufficient funds returns 400`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber, amount = BigDecimal("999999.00")
            )

            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { exists() }
            }
        }

        @Test
        fun `wrong destination account type returns 400`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, checkingAccountNumber, amount = BigDecimal("100.00")
            )

            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
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

            mockMvc.post("/api/v1/transactions/deposit") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("TRANSFER_DEPOSIT") }
                jsonPath("$.status") { value("COMPLETED") }
            }

            mockMvc.get("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(8000.0) }
            }

            mockMvc.get("/api/v1/accounts/$depositAccountNumber").andExpect {
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

            mockMvc.post("/api/v1/transactions/brokerage") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("TRANSFER_BROKERAGE") }
                jsonPath("$.status") { value("COMPLETED") }
            }

            mockMvc.get("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(5000.0) }
            }

            mockMvc.get("/api/v1/accounts/$brokerageAccountNumber").andExpect {
                jsonPath("$.balance") { value(5000.0) }
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

            mockMvc.post("/api/v1/transactions/gift") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("MONEY_GIFT") }
                jsonPath("$.sourceAccountNumber") { doesNotExist() }
                jsonPath("$.destinationAccountNumber") { value(savingsAccountNumber) }
            }

            mockMvc.get("/api/v1/accounts/$savingsAccountNumber").andExpect {
                jsonPath("$.balance") { value(5000.0) }
            }
        }
    }

    @Nested
    inner class Compensation {

        @Test
        fun `credits destination account`() {
            val request = TestDataFactory.compensationRequest(
                checkingAccountNumber, amount = BigDecimal("750.00"), description = "Возврат средств"
            )

            mockMvc.post("/api/v1/transactions/compensation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("COMPENSATION") }
                jsonPath("$.status") { value("COMPLETED") }
                jsonPath("$.amount") { value(750.0) }
                jsonPath("$.destinationAccountNumber") { value(checkingAccountNumber) }
            }

            mockMvc.get("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(10750.0) }
            }
        }

        @Test
        fun `idempotency returns same response`() {
            val request = TestDataFactory.compensationRequest(checkingAccountNumber, amount = BigDecimal("200.00"))

            mockMvc.post("/api/v1/transactions/compensation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect { status { isCreated() } }

            mockMvc.post("/api/v1/transactions/compensation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("COMPENSATION") }
            }

            mockMvc.get("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(10200.0) }
            }
        }

        @Test
        fun `account not found returns 404`() {
            val request = TestDataFactory.compensationRequest("9999999999999999999")

            mockMvc.post("/api/v1/transactions/compensation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isNotFound() }
            }
        }
    }

    @Nested
    inner class CreditPayment {

        @Test
        fun `debits source and credits destination`() {
            val depositAccountNumber = api.createAccount(userId, "DEPOSIT")

            val request = TestDataFactory.creditPaymentRequest(
                checkingAccountNumber, depositAccountNumber,
                amount = BigDecimal("1500.00"), description = "Выплата по кредиту"
            )

            mockMvc.post("/api/v1/transactions/credit-payment") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("CREDIT_PAYMENT") }
                jsonPath("$.status") { value("COMPLETED") }
                jsonPath("$.amount") { value(1500.0) }
                jsonPath("$.sourceAccountNumber") { value(checkingAccountNumber) }
                jsonPath("$.destinationAccountNumber") { value(depositAccountNumber) }
            }

            mockMvc.get("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(8500.0) }
            }

            mockMvc.get("/api/v1/accounts/$depositAccountNumber").andExpect {
                jsonPath("$.balance") { value(1500.0) }
            }
        }

        @Test
        fun `source account not found returns 404`() {
            val request = TestDataFactory.creditPaymentRequest("9999999999999999999", checkingAccountNumber)

            mockMvc.post("/api/v1/transactions/credit-payment") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isNotFound() }
            }
        }

        @Test
        fun `insufficient funds returns 400`() {
            val depositAccountNumber = api.createAccount(userId, "DEPOSIT")

            val request = TestDataFactory.creditPaymentRequest(
                checkingAccountNumber, depositAccountNumber,
                amount = BigDecimal("999999.00")
            )

            mockMvc.post("/api/v1/transactions/credit-payment") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
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

            mockMvc.post("/api/v1/transactions/sbp") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("SBP_TRANSFER") }
                jsonPath("$.status") { value("COMPLETED") }
                jsonPath("$.amount") { value(2000.0) }
            }

            mockMvc.get("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(8000.0) }
            }

            mockMvc.get("/api/v1/accounts/$recipientAccountNumber").andExpect {
                jsonPath("$.balance") { value(2000.0) }
            }
        }

        @Test
        fun `recipient not found returns 404`() {
            val request = TestDataFactory.sbpTransferRequest(
                checkingAccountNumber, "+70000000000"
            )

            mockMvc.post("/api/v1/transactions/sbp") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
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

            mockMvc.post("/api/v1/transactions/sbp") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class InterbankTransfer {

        @Test
        fun `success and balances updated`() {
            api.createCard(checkingAccountNumber, "4276000000000001")

            val recipientUserId = api.createUser(phoneNumber = "+79997654321")
            val recipientAccountNumber = api.createAccount(recipientUserId, "CHECKING")
            api.createCard(recipientAccountNumber, "4276000000000002")

            val request = TestDataFactory.interbankTransferRequest(
                "4276000000000001", "4276000000000002",
                amount = BigDecimal("1500.00"), description = "Межбанковский перевод"
            )

            mockMvc.post("/api/v1/transactions/interbank") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.transactionType") { value("INTERBANK_TRANSFER") }
                jsonPath("$.status") { value("COMPLETED") }
                jsonPath("$.amount") { value(1500.0) }
            }

            mockMvc.get("/api/v1/accounts/$checkingAccountNumber").andExpect {
                jsonPath("$.balance") { value(8500.0) }
            }

            mockMvc.get("/api/v1/accounts/$recipientAccountNumber").andExpect {
                jsonPath("$.balance") { value(1500.0) }
            }
        }

        @Test
        fun `source card not found returns 404`() {
            api.createCard(checkingAccountNumber, "4276000000000001")

            val request = TestDataFactory.interbankTransferRequest(
                "9999999999999999", "4276000000000001"
            )

            mockMvc.post("/api/v1/transactions/interbank") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isNotFound() }
            }
        }

        @Test
        fun `insufficient funds returns 400`() {
            api.createCard(checkingAccountNumber, "4276000000000001")

            val recipientUserId = api.createUser(phoneNumber = "+79997654321")
            val recipientAccountNumber = api.createAccount(recipientUserId, "CHECKING")
            api.createCard(recipientAccountNumber, "4276000000000002")

            val request = TestDataFactory.interbankTransferRequest(
                "4276000000000001", "4276000000000002", amount = BigDecimal("999999.00")
            )

            mockMvc.post("/api/v1/transactions/interbank") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
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

            val result = mockMvc.post("/api/v1/transactions/gift") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andReturn()

            val txId = objectMapper.readTree(result.response.contentAsString)["id"].asLong()

            mockMvc.get("/api/v1/transactions/$txId").andExpect {
                status { isOk() }
                jsonPath("$.id") { value(txId) }
                jsonPath("$.status") { value("COMPLETED") }
            }
        }

        @Test
        fun `not found returns 404`() {
            mockMvc.get("/api/v1/transactions/999999").andExpect {
                status { isNotFound() }
            }
        }

        @Test
        fun `by account - returns related transactions`() {
            repeat(3) {
                api.fundAccount(checkingAccountNumber, BigDecimal("100.00"))
            }

            mockMvc.get("/api/v1/transactions/account/$checkingAccountNumber").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(4) }
            }
        }
    }
}
