package by.svyat.core.transaction.service.impl

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import by.svyat.core.transaction.api.common.BusinessException
import by.svyat.core.transaction.api.dto.request.*
import by.svyat.core.transaction.api.dto.response.TransactionResponse
import by.svyat.core.transaction.entity.AccountEntity
import by.svyat.core.transaction.entity.CardEntity
import by.svyat.core.transaction.entity.TransactionEntity
import by.svyat.core.transaction.entity.UserEntity
import by.svyat.core.transaction.entity.enums.AccountType
import by.svyat.core.transaction.entity.enums.TransactionStatus
import by.svyat.core.transaction.entity.enums.TransactionType
import by.svyat.core.transaction.mapping.TransactionMapper
import by.svyat.core.transaction.repository.AccountRepository
import by.svyat.core.transaction.repository.CardRepository
import by.svyat.core.transaction.repository.TransactionRepository
import by.svyat.core.transaction.repository.UserRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class TransactionServiceImplTest {

    private val transactionRepository: TransactionRepository = mockk()
    private val accountRepository: AccountRepository = mockk()
    private val cardRepository: CardRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val transactionMapper: TransactionMapper = mockk()
    private val meterRegistry = SimpleMeterRegistry()

    private val service = TransactionServiceImpl(
        transactionRepository, accountRepository, cardRepository, userRepository, transactionMapper, meterRegistry
    )

    private val now = OffsetDateTime.now()

    private fun userEntity(id: Long = 1L, phone: String = "+79991234567") = UserEntity(
        id = id, firstName = "Иван", lastName = "Иванов", phoneNumber = phone
    )

    private fun accountEntity(
        id: Long = 1L,
        type: AccountType = AccountType.CHECKING,
        balance: BigDecimal = BigDecimal("10000.0000"),
        isActive: Boolean = true
    ) = AccountEntity(
        id = id,
        user = userEntity(),
        accountNumber = "4081781000000000000$id",
        accountType = type,
        balance = balance,
        isActive = isActive
    )

    private fun cardEntity(
        id: Long = 1L,
        cardNumber: String = "4276000000000001",
        account: AccountEntity = accountEntity(),
        isActive: Boolean = true
    ) = CardEntity(
        id = id,
        account = account,
        cardNumber = cardNumber,
        expiryDate = LocalDate.of(2028, 12, 31),
        isActive = isActive
    )

    private fun txResponse(
        id: Long = 1L,
        key: UUID = UUID.randomUUID(),
        type: TransactionType = TransactionType.TRANSFER_SAVINGS,
        sourceId: Long? = 1L,
        destId: Long? = 2L,
        amount: BigDecimal = BigDecimal("500.0000")
    ) = TransactionResponse(
        id = id,
        idempotencyKey = key,
        transactionType = type.name,
        status = TransactionStatus.COMPLETED.name,
        sourceAccountId = sourceId,
        destinationAccountId = destId,
        amount = amount,
        currency = "RUB",
        description = null,
        errorMessage = null,
        createdAt = now,
        completedAt = now
    )

    private fun stubIdempotency(key: UUID, existing: TransactionEntity? = null) {
        every { transactionRepository.findByIdempotencyKey(key) } returns existing
    }

    private fun stubSaveTransaction(response: TransactionResponse) {
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { transactionMapper.toResponse(any<TransactionEntity>()) } returns response
    }

    private fun stubAccountSave() {
        every { accountRepository.save(any()) } answers { firstArg() }
    }

    // ===== transferToSavings =====

    @Test
    fun `transferToSavings - success`() {
        val key = UUID.randomUUID()
        val source = accountEntity(id = 1L, type = AccountType.CHECKING, balance = BigDecimal("5000"))
        val dest = accountEntity(id = 2L, type = AccountType.SAVINGS)
        val expected = txResponse(key = key, type = TransactionType.TRANSFER_SAVINGS)

        stubIdempotency(key)
        every { accountRepository.findById(2L) } returns Optional.of(dest)
        every { accountRepository.findByIdForUpdate(1L) } returns source
        stubAccountSave()
        stubSaveTransaction(expected)

        val request = TransferRequest(key, 1L, 2L, BigDecimal("500"), "test")
        val result = service.transferToSavings(request)

        assertEquals(expected, result)
    }

    @Test
    fun `transferToSavings - wrong destination type throws BAD_REQUEST`() {
        val key = UUID.randomUUID()
        val dest = accountEntity(id = 2L, type = AccountType.CHECKING)

        stubIdempotency(key)
        every { accountRepository.findById(2L) } returns Optional.of(dest)

        val request = TransferRequest(key, 1L, 2L, BigDecimal("500"), null)

        val ex = assertThrows<BusinessException> { service.transferToSavings(request) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
    }

    @Test
    fun `transferToDeposit - success`() {
        val key = UUID.randomUUID()
        val source = accountEntity(id = 1L)
        val dest = accountEntity(id = 2L, type = AccountType.DEPOSIT)
        val expected = txResponse(key = key, type = TransactionType.TRANSFER_DEPOSIT)

        stubIdempotency(key)
        every { accountRepository.findById(2L) } returns Optional.of(dest)
        every { accountRepository.findByIdForUpdate(1L) } returns source
        stubAccountSave()
        stubSaveTransaction(expected)

        val result = service.transferToDeposit(TransferRequest(key, 1L, 2L, BigDecimal("500"), null))
        assertEquals(expected, result)
    }

    @Test
    fun `transferToBrokerage - success`() {
        val key = UUID.randomUUID()
        val source = accountEntity(id = 1L)
        val dest = accountEntity(id = 2L, type = AccountType.BROKERAGE)
        val expected = txResponse(key = key, type = TransactionType.TRANSFER_BROKERAGE)

        stubIdempotency(key)
        every { accountRepository.findById(2L) } returns Optional.of(dest)
        every { accountRepository.findByIdForUpdate(1L) } returns source
        stubAccountSave()
        stubSaveTransaction(expected)

        val result = service.transferToBrokerage(TransferRequest(key, 1L, 2L, BigDecimal("500"), null))
        assertEquals(expected, result)
    }

    // ===== idempotency =====

    @Test
    fun `transfer - idempotency key already exists returns existing transaction`() {
        val key = UUID.randomUUID()
        val existingTx = mockk<TransactionEntity>()
        val expected = txResponse(key = key)

        every { transactionRepository.findByIdempotencyKey(key) } returns existingTx
        every { transactionMapper.toResponse(existingTx) } returns expected

        val result = service.transferToSavings(TransferRequest(key, 1L, 2L, BigDecimal("500"), null))

        assertEquals(expected, result)
        verify(exactly = 0) { accountRepository.findByIdForUpdate(any()) }
    }

    // ===== lockAndValidateSource =====

    @Test
    fun `transfer - source account not found throws NOT_FOUND`() {
        val key = UUID.randomUUID()
        val dest = accountEntity(id = 2L, type = AccountType.SAVINGS)

        stubIdempotency(key)
        every { accountRepository.findById(2L) } returns Optional.of(dest)
        every { accountRepository.findByIdForUpdate(1L) } returns null

        val ex = assertThrows<BusinessException> {
            service.transferToSavings(TransferRequest(key, 1L, 2L, BigDecimal("500"), null))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `transfer - source account inactive throws BAD_REQUEST`() {
        val key = UUID.randomUUID()
        val source = accountEntity(id = 1L, isActive = false)
        val dest = accountEntity(id = 2L, type = AccountType.SAVINGS)

        stubIdempotency(key)
        every { accountRepository.findById(2L) } returns Optional.of(dest)
        every { accountRepository.findByIdForUpdate(1L) } returns source

        val ex = assertThrows<BusinessException> {
            service.transferToSavings(TransferRequest(key, 1L, 2L, BigDecimal("500"), null))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
    }

    @Test
    fun `transfer - insufficient funds throws BAD_REQUEST`() {
        val key = UUID.randomUUID()
        val source = accountEntity(id = 1L, balance = BigDecimal("100"))
        val dest = accountEntity(id = 2L, type = AccountType.SAVINGS)

        stubIdempotency(key)
        every { accountRepository.findById(2L) } returns Optional.of(dest)
        every { accountRepository.findByIdForUpdate(1L) } returns source

        val ex = assertThrows<BusinessException> {
            service.transferToSavings(TransferRequest(key, 1L, 2L, BigDecimal("500"), null))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
    }

    // ===== destination not found =====

    @Test
    fun `transfer - destination account not found throws NOT_FOUND`() {
        val key = UUID.randomUUID()

        stubIdempotency(key)
        every { accountRepository.findById(2L) } returns Optional.empty()

        val ex = assertThrows<BusinessException> {
            service.transferToSavings(TransferRequest(key, 1L, 2L, BigDecimal("500"), null))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    // ===== balance update =====

    @Test
    fun `transfer - balances are correctly updated`() {
        val key = UUID.randomUUID()
        val source = accountEntity(id = 1L, balance = BigDecimal("5000"))
        val dest = accountEntity(id = 2L, type = AccountType.SAVINGS, balance = BigDecimal("1000"))
        val savedAccounts = mutableListOf<AccountEntity>()

        stubIdempotency(key)
        every { accountRepository.findById(2L) } returns Optional.of(dest)
        every { accountRepository.findByIdForUpdate(1L) } returns source
        every { accountRepository.save(capture(savedAccounts)) } answers { firstArg() }
        stubSaveTransaction(txResponse(key = key))

        service.transferToSavings(TransferRequest(key, 1L, 2L, BigDecimal("500"), null))

        assertEquals(BigDecimal("4500"), savedAccounts[0].balance) // source debited
        assertEquals(BigDecimal("1500"), savedAccounts[1].balance) // dest credited
    }

    // ===== interbankTransfer =====

    @Test
    fun `interbankTransfer - success`() {
        val key = UUID.randomUUID()
        val sourceAccount = accountEntity(id = 1L, balance = BigDecimal("5000"))
        val destAccount = accountEntity(id = 2L)
        val sourceCard = cardEntity(id = 1L, cardNumber = "4276000000000001", account = sourceAccount)
        val destCard = cardEntity(id = 2L, cardNumber = "4276000000000002", account = destAccount)
        val expected = txResponse(key = key, type = TransactionType.INTERBANK_TRANSFER)

        stubIdempotency(key)
        every { cardRepository.findByCardNumber("4276000000000001") } returns sourceCard
        every { cardRepository.findByCardNumber("4276000000000002") } returns destCard
        every { accountRepository.findByIdForUpdate(1L) } returns sourceAccount
        every { accountRepository.findById(2L) } returns Optional.of(destAccount)
        stubAccountSave()
        stubSaveTransaction(expected)

        val request = InterbankTransferRequest(key, "4276000000000001", "4276000000000002", BigDecimal("500"), null)
        val result = service.interbankTransfer(request)

        assertEquals(expected, result)
    }

    @Test
    fun `interbankTransfer - source card not found throws NOT_FOUND`() {
        val key = UUID.randomUUID()
        stubIdempotency(key)
        every { cardRepository.findByCardNumber("0000") } returns null

        val ex = assertThrows<BusinessException> {
            service.interbankTransfer(InterbankTransferRequest(key, "0000", "1111", BigDecimal("500"), null))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `interbankTransfer - destination card not found throws NOT_FOUND`() {
        val key = UUID.randomUUID()
        val sourceCard = cardEntity()
        stubIdempotency(key)
        every { cardRepository.findByCardNumber("4276000000000001") } returns sourceCard
        every { cardRepository.findByCardNumber("0000") } returns null

        val ex = assertThrows<BusinessException> {
            service.interbankTransfer(InterbankTransferRequest(key, "4276000000000001", "0000", BigDecimal("500"), null))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `interbankTransfer - inactive source card throws BAD_REQUEST`() {
        val key = UUID.randomUUID()
        val sourceCard = cardEntity(isActive = false)
        val destCard = cardEntity(id = 2L, cardNumber = "4276000000000002")
        stubIdempotency(key)
        every { cardRepository.findByCardNumber("4276000000000001") } returns sourceCard
        every { cardRepository.findByCardNumber("4276000000000002") } returns destCard

        val ex = assertThrows<BusinessException> {
            service.interbankTransfer(InterbankTransferRequest(key, "4276000000000001", "4276000000000002", BigDecimal("500"), null))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
    }

    @Test
    fun `interbankTransfer - inactive destination card throws BAD_REQUEST`() {
        val key = UUID.randomUUID()
        val sourceCard = cardEntity()
        val destCard = cardEntity(id = 2L, cardNumber = "4276000000000002", isActive = false)
        stubIdempotency(key)
        every { cardRepository.findByCardNumber("4276000000000001") } returns sourceCard
        every { cardRepository.findByCardNumber("4276000000000002") } returns destCard

        val ex = assertThrows<BusinessException> {
            service.interbankTransfer(InterbankTransferRequest(key, "4276000000000001", "4276000000000002", BigDecimal("500"), null))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
    }

    // ===== sbpTransfer =====

    @Test
    fun `sbpTransfer - success`() {
        val key = UUID.randomUUID()
        val recipient = userEntity(id = 2L, phone = "+79990000000")
        val destAccount = accountEntity(id = 3L, type = AccountType.CHECKING)
        val sourceAccount = accountEntity(id = 1L, balance = BigDecimal("5000"))
        val expected = txResponse(key = key, type = TransactionType.SBP_TRANSFER)

        stubIdempotency(key)
        every { userRepository.findByPhoneNumber("+79990000000") } returns recipient
        every { accountRepository.findByUserIdAndAccountType(2L, AccountType.CHECKING) } returns destAccount
        every { accountRepository.findByIdForUpdate(1L) } returns sourceAccount
        stubAccountSave()
        stubSaveTransaction(expected)

        val request = SbpTransferRequest(key, 1L, "+79990000000", BigDecimal("500"), null)
        val result = service.sbpTransfer(request)

        assertEquals(expected, result)
    }

    @Test
    fun `sbpTransfer - recipient not found throws NOT_FOUND`() {
        val key = UUID.randomUUID()
        stubIdempotency(key)
        every { userRepository.findByPhoneNumber("+70000000000") } returns null

        val ex = assertThrows<BusinessException> {
            service.sbpTransfer(SbpTransferRequest(key, 1L, "+70000000000", BigDecimal("500"), null))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `sbpTransfer - recipient has no checking account throws NOT_FOUND`() {
        val key = UUID.randomUUID()
        val recipient = userEntity(id = 2L, phone = "+79990000000")
        stubIdempotency(key)
        every { userRepository.findByPhoneNumber("+79990000000") } returns recipient
        every { accountRepository.findByUserIdAndAccountType(2L, AccountType.CHECKING) } returns null

        val ex = assertThrows<BusinessException> {
            service.sbpTransfer(SbpTransferRequest(key, 1L, "+79990000000", BigDecimal("500"), null))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    // ===== credit-only operations =====

    @Test
    fun `processMoneyGift - success`() {
        val key = UUID.randomUUID()
        val dest = accountEntity(id = 1L)
        val expected = txResponse(key = key, type = TransactionType.MONEY_GIFT, sourceId = null, destId = 1L)

        stubIdempotency(key)
        every { accountRepository.findById(1L) } returns Optional.of(dest)
        stubAccountSave()
        stubSaveTransaction(expected)

        val result = service.processMoneyGift(MoneyGiftRequest(key, 1L, BigDecimal("1000"), "Подарок"))
        assertEquals(expected, result)
    }

    @Test
    fun `processCompensation - success`() {
        val key = UUID.randomUUID()
        val dest = accountEntity(id = 1L)
        val expected = txResponse(key = key, type = TransactionType.COMPENSATION, sourceId = null, destId = 1L)

        stubIdempotency(key)
        every { accountRepository.findById(1L) } returns Optional.of(dest)
        stubAccountSave()
        stubSaveTransaction(expected)

        val result = service.processCompensation(CompensationRequest(key, 1L, BigDecimal("500"), null))
        assertEquals(expected, result)
    }

    @Test
    fun `processCreditPayment - success`() {
        val key = UUID.randomUUID()
        val dest = accountEntity(id = 1L)
        val expected = txResponse(key = key, type = TransactionType.CREDIT_PAYMENT, sourceId = null, destId = 1L)

        stubIdempotency(key)
        every { accountRepository.findById(1L) } returns Optional.of(dest)
        stubAccountSave()
        stubSaveTransaction(expected)

        val result = service.processCreditPayment(CreditPaymentRequest(key, 1L, BigDecimal("300"), null))
        assertEquals(expected, result)
    }

    @Test
    fun `creditOnly - destination not found throws NOT_FOUND`() {
        val key = UUID.randomUUID()
        stubIdempotency(key)
        every { accountRepository.findById(99L) } returns Optional.empty()

        val ex = assertThrows<BusinessException> {
            service.processMoneyGift(MoneyGiftRequest(key, 99L, BigDecimal("1000"), null))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `creditOnly - inactive destination throws BAD_REQUEST`() {
        val key = UUID.randomUUID()
        val dest = accountEntity(id = 1L, isActive = false)
        stubIdempotency(key)
        every { accountRepository.findById(1L) } returns Optional.of(dest)

        val ex = assertThrows<BusinessException> {
            service.processMoneyGift(MoneyGiftRequest(key, 1L, BigDecimal("1000"), null))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
    }

    @Test
    fun `creditOnly - balance is increased`() {
        val key = UUID.randomUUID()
        val dest = accountEntity(id = 1L, balance = BigDecimal("1000"))
        val accountSlot = slot<AccountEntity>()

        stubIdempotency(key)
        every { accountRepository.findById(1L) } returns Optional.of(dest)
        every { accountRepository.save(capture(accountSlot)) } answers { firstArg() }
        stubSaveTransaction(txResponse(key = key, type = TransactionType.MONEY_GIFT))

        service.processMoneyGift(MoneyGiftRequest(key, 1L, BigDecimal("500"), null))

        assertEquals(BigDecimal("1500"), accountSlot.captured.balance)
    }

    // ===== getTransaction =====

    @Test
    fun `getTransaction - success`() {
        val entity = mockk<TransactionEntity>()
        val expected = txResponse()

        every { transactionRepository.findById(1L) } returns Optional.of(entity)
        every { transactionMapper.toResponse(entity) } returns expected

        val result = service.getTransaction(1L)
        assertEquals(expected, result)
    }

    @Test
    fun `getTransaction - not found throws NOT_FOUND`() {
        every { transactionRepository.findById(99L) } returns Optional.empty()

        val ex = assertThrows<BusinessException> { service.getTransaction(99L) }
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    // ===== getTransactionsByAccount =====

    @Test
    fun `getTransactionsByAccount - returns transactions`() {
        val tx1 = mockk<TransactionEntity>()
        val tx2 = mockk<TransactionEntity>()
        val resp1 = txResponse(id = 1L)
        val resp2 = txResponse(id = 2L)

        every { transactionRepository.findAllBySourceAccountIdOrDestinationAccountId(1L, 1L) } returns listOf(tx1, tx2)
        every { transactionMapper.toResponse(tx1) } returns resp1
        every { transactionMapper.toResponse(tx2) } returns resp2

        val result = service.getTransactionsByAccount(1L)

        assertEquals(2, result.size)
    }
}
