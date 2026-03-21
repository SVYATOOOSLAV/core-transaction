package by.svyat.core.transaction.mapping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import by.svyat.core.transaction.entity.AccountEntity
import by.svyat.core.transaction.entity.TransactionEntity
import by.svyat.core.transaction.entity.UserEntity
import by.svyat.core.transaction.entity.enums.AccountType
import by.svyat.core.transaction.entity.enums.TransactionStatus
import by.svyat.core.transaction.entity.enums.TransactionType
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

class MapperTest {

    private val userMapper = UserMapper()
    private val accountMapper = AccountMapper()
    private val transactionMapper = TransactionMapper()

    private val now = OffsetDateTime.now()

    // ===== UserMapper =====

    @Test
    fun `UserMapper - maps all fields correctly`() {
        val entity = UserEntity(
            id = 1L,
            firstName = "Иван",
            lastName = "Иванов",
            patronymic = "Иванович",
            phoneNumber = "+79991234567",
            email = "ivan@mail.ru"
        )

        val response = userMapper.toResponse(entity)

        assertEquals(1L, response.id)
        assertEquals("Иван", response.firstName)
        assertEquals("Иванов", response.lastName)
        assertEquals("Иванович", response.patronymic)
        assertEquals("+79991234567", response.phoneNumber)
        assertEquals("ivan@mail.ru", response.email)
    }

    @Test
    fun `UserMapper - maps nullable fields as null`() {
        val entity = UserEntity(
            id = 2L,
            firstName = "Пётр",
            lastName = "Петров",
            patronymic = null,
            phoneNumber = "+79990000000",
            email = null
        )

        val response = userMapper.toResponse(entity)

        assertNull(response.patronymic)
        assertNull(response.email)
    }

    // ===== AccountMapper =====

    @Test
    fun `AccountMapper - maps all fields correctly`() {
        val user = UserEntity(id = 5L, firstName = "Иван", lastName = "Иванов", phoneNumber = "+79991234567")
        val entity = AccountEntity(
            id = 10L,
            user = user,
            accountNumber = "40817810000000000001",
            accountType = AccountType.SAVINGS,
            currency = "USD",
            balance = BigDecimal("12345.6789"),
            isActive = true
        )

        val response = accountMapper.toResponse(entity)

        assertEquals(10L, response.id)
        assertEquals(5L, response.userId)
        assertEquals("40817810000000000001", response.accountNumber)
        assertEquals("SAVINGS", response.accountType)
        assertEquals("USD", response.currency)
        assertEquals(BigDecimal("12345.6789"), response.balance)
        assertEquals(true, response.isActive)
    }

    @Test
    fun `AccountMapper - maps inactive account`() {
        val user = UserEntity(id = 1L, firstName = "A", lastName = "B", phoneNumber = "+7")
        val entity = AccountEntity(
            id = 1L,
            user = user,
            accountNumber = "40817810000000000002",
            accountType = AccountType.BROKERAGE,
            isActive = false
        )

        val response = accountMapper.toResponse(entity)

        assertEquals("BROKERAGE", response.accountType)
        assertEquals(false, response.isActive)
    }

    // ===== TransactionMapper =====

    @Test
    fun `TransactionMapper - maps transaction with both accounts`() {
        val user = UserEntity(id = 1L, firstName = "A", lastName = "B", phoneNumber = "+7")
        val sourceAccount = AccountEntity(
            id = 1L, user = user, accountNumber = "SRC", accountType = AccountType.CHECKING
        )
        val destAccount = AccountEntity(
            id = 2L, user = user, accountNumber = "DST", accountType = AccountType.SAVINGS
        )
        val completedAt = OffsetDateTime.now()
        val key = UUID.randomUUID()

        val entity = TransactionEntity(
            id = 100L,
            idempotencyKey = key,
            transactionType = TransactionType.TRANSFER_SAVINGS,
            status = TransactionStatus.COMPLETED,
            sourceAccount = sourceAccount,
            destinationAccount = destAccount,
            amount = BigDecimal("999.50"),
            currency = "RUB",
            description = "test description",
            errorMessage = null,
            completedAt = completedAt
        )

        val response = transactionMapper.toResponse(entity)

        assertEquals(100L, response.id)
        assertEquals(key, response.idempotencyKey)
        assertEquals("TRANSFER_SAVINGS", response.transactionType)
        assertEquals("COMPLETED", response.status)
        assertEquals(1L, response.sourceAccountId)
        assertEquals(2L, response.destinationAccountId)
        assertEquals(BigDecimal("999.50"), response.amount)
        assertEquals("RUB", response.currency)
        assertEquals("test description", response.description)
        assertNull(response.errorMessage)
        assertEquals(completedAt, response.completedAt)
    }

    @Test
    fun `TransactionMapper - maps credit-only transaction without source`() {
        val user = UserEntity(id = 1L, firstName = "A", lastName = "B", phoneNumber = "+7")
        val destAccount = AccountEntity(
            id = 5L, user = user, accountNumber = "DST", accountType = AccountType.CHECKING
        )

        val entity = TransactionEntity(
            id = 200L,
            idempotencyKey = UUID.randomUUID(),
            transactionType = TransactionType.MONEY_GIFT,
            status = TransactionStatus.COMPLETED,
            sourceAccount = null,
            destinationAccount = destAccount,
            amount = BigDecimal("1000"),
            completedAt = OffsetDateTime.now()
        )

        val response = transactionMapper.toResponse(entity)

        assertNull(response.sourceAccountId)
        assertEquals(5L, response.destinationAccountId)
        assertEquals("MONEY_GIFT", response.transactionType)
    }

    @Test
    fun `TransactionMapper - maps failed transaction with error message`() {
        val entity = TransactionEntity(
            id = 300L,
            idempotencyKey = UUID.randomUUID(),
            transactionType = TransactionType.INTERBANK_TRANSFER,
            status = TransactionStatus.FAILED,
            amount = BigDecimal("500"),
            errorMessage = "Insufficient funds"
        )

        val response = transactionMapper.toResponse(entity)

        assertEquals("FAILED", response.status)
        assertEquals("Insufficient funds", response.errorMessage)
        assertNull(response.sourceAccountId)
        assertNull(response.destinationAccountId)
        assertNull(response.completedAt)
    }
}
