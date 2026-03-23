package by.svyat.core.transaction.integration

import by.svyat.core.transaction.IntegrationTestBase
import by.svyat.core.transaction.TestApiClient
import by.svyat.core.transaction.TestDataFactory
import by.svyat.core.transaction.entity.enums.OutboxEventType
import by.svyat.core.transaction.outbox.enums.OutboxAggregateType
import by.svyat.core.transaction.repository.OutboxMessageRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import kotlin.math.abs

class OutboxProducerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var api: TestApiClient

    @Autowired
    private lateinit var outboxMessageRepository: OutboxMessageRepository

    private lateinit var checkingAccountNumber: String
    private lateinit var savingsAccountNumber: String

    @BeforeEach
    fun setUp() {
        val accounts = api.createUserWithCheckingAndSavings()
        checkingAccountNumber = accounts.checkingAccountNumber
        savingsAccountNumber = accounts.savingsAccountNumber
        // fundAccount creates a MONEY_GIFT transaction which also writes to outbox
        api.fundAccount(checkingAccountNumber)
    }

    @Nested
    inner class AtomicityWithTransaction {

        @Test
        fun `outbox message is created together with transaction on debit-credit operation`() {
            // fundAccount already created 1 outbox message (MONEY_GIFT)
            val initialCount = outboxMessageRepository.count()

            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber,
                amount = BigDecimal("1000.00"), description = "На накопления"
            )

            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
            }

            assertEquals(initialCount + 1, outboxMessageRepository.count())
        }

        @Test
        fun `outbox message is created together with transaction on credit-only operation`() {
            val initialCount = outboxMessageRepository.count()

            val request = TestDataFactory.moneyGiftRequest(
                destinationAccountNumber = checkingAccountNumber,
                amount = BigDecimal("500.00"),
                description = "Подарок"
            )

            mockMvc.post("/api/v1/transactions/gift") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
            }

            assertEquals(initialCount + 1, outboxMessageRepository.count())
        }

        @Test
        fun `no outbox message is created when transaction fails`() {
            val initialCount = outboxMessageRepository.count()

            // insufficient funds — balance is 10000, requesting 99999
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber,
                amount = BigDecimal("99999.00")
            )

            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
            }

            assertEquals(initialCount, outboxMessageRepository.count())
        }

        @Test
        fun `no outbox message is created when destination account not found`() {
            val initialCount = outboxMessageRepository.count()

            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, "9999999999999999999",
                amount = BigDecimal("100.00")
            )

            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isNotFound() }
            }

            assertEquals(initialCount, outboxMessageRepository.count())
        }
    }

    @Nested
    inner class MessageContent {

        @Test
        fun `debit-credit operation creates message with correct aggregate type and event type`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber,
                amount = BigDecimal("1000.00")
            )

            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
            }

            val messages = outboxMessageRepository.findAll()
            // last message is the transfer (first is fundAccount's MONEY_GIFT)
            val transferMessage = messages.sortedBy { it.id }.last()

            assertEquals(OutboxAggregateType.TRANSACTION.name, transferMessage.aggregateType)
            assertEquals(OutboxEventType.TRANSFER_COMPLETED.name, transferMessage.eventType)
        }

        @Test
        fun `debit-credit operation uses source account number as partition key`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber,
                amount = BigDecimal("1000.00")
            )

            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
            }

            val transferMessage = outboxMessageRepository.findAll().sortedBy { it.id }.last()

            assertEquals(checkingAccountNumber, transferMessage.partitionKey)
        }

        @Test
        fun `credit-only operation uses destination account number as partition key`() {
            val request = TestDataFactory.compensationRequest(
                destinationAccountNumber = checkingAccountNumber,
                amount = BigDecimal("200.00")
            )

            mockMvc.post("/api/v1/transactions/compensation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
            }

            val compensationMessage = outboxMessageRepository.findAll().sortedBy { it.id }.last()

            assertEquals(checkingAccountNumber, compensationMessage.partitionKey)
        }

        @Test
        fun `payload contains transaction details as valid JSON`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber,
                amount = BigDecimal("2500.00")
            )

            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
            }

            val transferMessage = outboxMessageRepository.findAll().sortedBy { it.id }.last()
            val payload = objectMapper.readTree(transferMessage.payload)

            assertTrue(payload.has("transactionId"))
            assertEquals("TRANSFER_SAVINGS", payload["type"].asText())
            assertEquals(checkingAccountNumber, payload["sourceAccountNumber"].asText())
            assertEquals(savingsAccountNumber, payload["destinationAccountNumber"].asText())
            assertEquals(2500.0, payload["amount"].asDouble())
            assertEquals("RUB", payload["currency"].asText())
            assertEquals("COMPLETED", payload["status"].asText())
        }

        @Test
        fun `credit-only payload has null source account`() {
            val request = TestDataFactory.moneyGiftRequest(
                destinationAccountNumber = savingsAccountNumber,
                amount = BigDecimal("777.00")
            )

            mockMvc.post("/api/v1/transactions/gift") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
            }

            val giftMessage = outboxMessageRepository.findAll().sortedBy { it.id }.last()
            val payload = objectMapper.readTree(giftMessage.payload)

            assertTrue(payload["sourceAccountNumber"].isNull)
            assertEquals(savingsAccountNumber, payload["destinationAccountNumber"].asText())
            assertEquals("MONEY_GIFT", payload["type"].asText())
        }

        @Test
        fun `aggregate id matches transaction id from response`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber,
                amount = BigDecimal("500.00")
            )

            val result = mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
            }.andReturn()

            val responseJson = objectMapper.readTree(result.response.contentAsString)
            val transactionId = responseJson["id"].asText()

            val transferMessage = outboxMessageRepository.findAll().sortedBy { it.id }.last()
            assertEquals(transactionId, transferMessage.aggregateId)
        }
    }

    @Nested
    inner class Partitioning {

        @Test
        fun `partition number is computed correctly from partition key`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber,
                amount = BigDecimal("100.00")
            )

            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
            }

            val transferMessage = outboxMessageRepository.findAll().sortedBy { it.id }.last()
            val expectedPartition = abs(checkingAccountNumber.hashCode()) % 8

            assertEquals(expectedPartition, transferMessage.partitionNum)
        }

        @Test
        fun `partition number is within valid range`() {
            val allMessages = outboxMessageRepository.findAll()

            for (message in allMessages) {
                assertTrue(message.partitionNum in 0 until 8) {
                    "Partition ${message.partitionNum} for key '${message.partitionKey}' is out of range [0, 8)"
                }
            }
        }

        @Test
        fun `events for same account always land in same partition`() {
            // Create multiple transactions for the same account
            repeat(3) {
                val request = TestDataFactory.moneyGiftRequest(
                    destinationAccountNumber = checkingAccountNumber,
                    amount = BigDecimal("100.00")
                )
                mockMvc.post("/api/v1/transactions/gift") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(request)
                }.andExpect {
                    status { isCreated() }
                }
            }

            val messagesForAccount = outboxMessageRepository.findAll()
                .filter { it.partitionKey == checkingAccountNumber }

            assertTrue(messagesForAccount.size >= 3)
            val partitions = messagesForAccount.map { it.partitionNum }.toSet()
            assertEquals(1, partitions.size) {
                "All messages for the same account should be in the same partition, but got $partitions"
            }
        }
    }

    @Nested
    inner class IdempotencyInteraction {

        @Test
        fun `idempotent request does not create duplicate outbox message`() {
            val request = TestDataFactory.transferRequest(
                checkingAccountNumber, savingsAccountNumber,
                amount = BigDecimal("500.00")
            )

            // First call
            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
            }

            val countAfterFirst = outboxMessageRepository.count()

            // Second call with same idempotency key
            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
            }

            assertEquals(countAfterFirst, outboxMessageRepository.count()) {
                "Idempotent request should not create a duplicate outbox message"
            }
        }
    }

    @Nested
    inner class MultipleTransactionTypes {

        @Test
        fun `each transaction type creates outbox message`() {
            val initialCount = outboxMessageRepository.count()

            // 1. Transfer to savings
            mockMvc.post("/api/v1/transactions/savings") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    TestDataFactory.transferRequest(checkingAccountNumber, savingsAccountNumber, BigDecimal("100.00"))
                )
            }.andExpect { status { isCreated() } }

            // 2. Compensation
            mockMvc.post("/api/v1/transactions/compensation") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    TestDataFactory.compensationRequest(checkingAccountNumber, BigDecimal("50.00"))
                )
            }.andExpect { status { isCreated() } }

            // 3. Money gift
            mockMvc.post("/api/v1/transactions/gift") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    TestDataFactory.moneyGiftRequest(savingsAccountNumber, BigDecimal("200.00"))
                )
            }.andExpect { status { isCreated() } }

            assertEquals(initialCount + 3, outboxMessageRepository.count())

            val newMessages = outboxMessageRepository.findAll()
                .sortedBy { it.id }
                .takeLast(3)

            val payloadTypes = newMessages.map { objectMapper.readTree(it.payload)["type"].asText() }

            assertTrue("TRANSFER_SAVINGS" in payloadTypes)
            assertTrue("COMPENSATION" in payloadTypes)
            assertTrue("MONEY_GIFT" in payloadTypes)
        }
    }

    @Nested
    inner class MessageOrdering {

        @Test
        fun `messages have monotonically increasing ids within the same partition`() {
            // Create several transactions targeting the same account → same partition
            repeat(5) {
                mockMvc.post("/api/v1/transactions/gift") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(
                        TestDataFactory.moneyGiftRequest(checkingAccountNumber, BigDecimal("10.00"))
                    )
                }.andExpect { status { isCreated() } }
            }

            val messagesInPartition = outboxMessageRepository.findAll()
                .filter { it.partitionKey == checkingAccountNumber }
                .sortedBy { it.id }

            for (i in 1 until messagesInPartition.size) {
                assertTrue(messagesInPartition[i].id > messagesInPartition[i - 1].id) {
                    "Message ids should be monotonically increasing within a partition"
                }
            }
        }
    }
}
