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
import by.svyat.core.transaction.api.controller.impl.TransactionController
import by.svyat.core.transaction.api.dto.request.*
import by.svyat.core.transaction.api.dto.response.TransactionResponse
import by.svyat.core.transaction.entity.enums.TransactionStatus
import by.svyat.core.transaction.entity.enums.TransactionType
import by.svyat.core.transaction.service.TransactionService
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

@WebMvcTest(TransactionController::class)
class TransactionControllerTest {

    @TestConfiguration
    class Config {
        @Bean
        fun transactionService(): TransactionService = mockk()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionService: TransactionService

    private val now = OffsetDateTime.now()

    private fun txResponse(
        type: TransactionType = TransactionType.TRANSFER_SAVINGS,
        sourceId: Long? = 1L,
        destId: Long? = 2L
    ) = TransactionResponse(
        id = 1L,
        idempotencyKey = UUID.randomUUID(),
        transactionType = type.name,
        status = TransactionStatus.COMPLETED.name,
        sourceAccountId = sourceId,
        destinationAccountId = destId,
        amount = BigDecimal("500.00"),
        currency = "RUB",
        description = "test",
        errorMessage = null,
        createdAt = now,
        completedAt = now
    )

    // ===== Internal transfers =====

    @Test
    fun `POST savings - returns 201`() {
        every { transactionService.transferToSavings(any()) } returns txResponse(TransactionType.TRANSFER_SAVINGS)

        val request = TransferRequest(UUID.randomUUID(), 1L, 2L, BigDecimal("500.00"), "test")

        mockMvc.post("/api/v1/transactions/savings") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.transactionType") { value("TRANSFER_SAVINGS") }
            jsonPath("$.status") { value("COMPLETED") }
        }
    }

    @Test
    fun `POST deposit - returns 201`() {
        every { transactionService.transferToDeposit(any()) } returns txResponse(TransactionType.TRANSFER_DEPOSIT)

        val request = TransferRequest(UUID.randomUUID(), 1L, 2L, BigDecimal("500.00"), null)

        mockMvc.post("/api/v1/transactions/deposit") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.transactionType") { value("TRANSFER_DEPOSIT") }
        }
    }

    @Test
    fun `POST brokerage - returns 201`() {
        every { transactionService.transferToBrokerage(any()) } returns txResponse(TransactionType.TRANSFER_BROKERAGE)

        val request = TransferRequest(UUID.randomUUID(), 1L, 2L, BigDecimal("500.00"), null)

        mockMvc.post("/api/v1/transactions/brokerage") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.transactionType") { value("TRANSFER_BROKERAGE") }
        }
    }

    // ===== Interbank transfer =====

    @Test
    fun `POST interbank - returns 201`() {
        every { transactionService.interbankTransfer(any()) } returns txResponse(TransactionType.INTERBANK_TRANSFER)

        val request = InterbankTransferRequest(
            UUID.randomUUID(), "4276000000000001", "4276000000000002", BigDecimal("500"), null
        )

        mockMvc.post("/api/v1/transactions/interbank") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.transactionType") { value("INTERBANK_TRANSFER") }
        }
    }

    // ===== SBP transfer =====

    @Test
    fun `POST sbp - returns 201`() {
        every { transactionService.sbpTransfer(any()) } returns txResponse(TransactionType.SBP_TRANSFER)

        val request = SbpTransferRequest(UUID.randomUUID(), 1L, "+79990000000", BigDecimal("500"), null)

        mockMvc.post("/api/v1/transactions/sbp") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.transactionType") { value("SBP_TRANSFER") }
        }
    }

    // ===== Credit-only =====

    @Test
    fun `POST gift - returns 201`() {
        every { transactionService.processMoneyGift(any()) } returns txResponse(TransactionType.MONEY_GIFT, sourceId = null, destId = 1L)

        val request = MoneyGiftRequest(UUID.randomUUID(), 1L, BigDecimal("1000"), "Подарок")

        mockMvc.post("/api/v1/transactions/gift") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.transactionType") { value("MONEY_GIFT") }
        }
    }

    @Test
    fun `POST compensation - returns 201`() {
        every { transactionService.processCompensation(any()) } returns txResponse(TransactionType.COMPENSATION, sourceId = null)

        val request = CompensationRequest(UUID.randomUUID(), 1L, BigDecimal("500"), null)

        mockMvc.post("/api/v1/transactions/compensation") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `POST credit-payment - returns 201`() {
        every { transactionService.processCreditPayment(any()) } returns txResponse(TransactionType.CREDIT_PAYMENT, sourceId = null)

        val request = CreditPaymentRequest(UUID.randomUUID(), 1L, BigDecimal("300"), null)

        mockMvc.post("/api/v1/transactions/credit-payment") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
        }
    }

    // ===== Validation =====

    @Test
    fun `POST savings - invalid amount returns 400`() {
        val request = mapOf(
            "idempotencyKey" to UUID.randomUUID(),
            "sourceAccountId" to 1,
            "destinationAccountId" to 2,
            "amount" to "0.001"
        )

        mockMvc.post("/api/v1/transactions/savings") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST savings - zero amount returns 400`() {
        val request = TransferRequest(UUID.randomUUID(), 1L, 2L, BigDecimal("0.00"), null)

        mockMvc.post("/api/v1/transactions/savings") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ===== Business error =====

    @Test
    fun `POST savings - insufficient funds returns 400`() {
        every { transactionService.transferToSavings(any()) } throws
                BusinessException(HttpStatus.BAD_REQUEST, "Insufficient funds")

        val request = TransferRequest(UUID.randomUUID(), 1L, 2L, BigDecimal("500"), null)

        mockMvc.post("/api/v1/transactions/savings") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Insufficient funds") }
        }
    }

    // ===== GET endpoints =====

    @Test
    fun `GET transaction - returns 200`() {
        every { transactionService.getTransaction(1L) } returns txResponse()

        mockMvc.get("/api/v1/transactions/1").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(1) }
        }
    }

    @Test
    fun `GET transaction - not found returns 404`() {
        every { transactionService.getTransaction(99L) } throws
                BusinessException(HttpStatus.NOT_FOUND, "Transaction with id 99 not found")

        mockMvc.get("/api/v1/transactions/99").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET transactions by account - returns list`() {
        every { transactionService.getTransactionsByAccount(1L) } returns listOf(txResponse(), txResponse())

        mockMvc.get("/api/v1/transactions/account/1").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
    }
}
