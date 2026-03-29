package by.svyat.core.transaction.service.impl

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import by.svyat.core.transaction.api.common.BusinessException
import by.svyat.core.transaction.api.dto.response.AccountResponse
import by.svyat.core.transaction.entity.AccountEntity
import by.svyat.core.transaction.entity.UserEntity
import by.svyat.core.transaction.entity.enums.AccountType
import by.svyat.core.transaction.mapping.AccountMapper
import by.svyat.core.transaction.repository.AccountRepository
import by.svyat.core.transaction.repository.CardRepository
import by.svyat.core.transaction.repository.UserRepository
import by.svyat.core.transaction.component.AccountNumberGenerator
import by.svyat.core.transaction.component.CardNumberGenerator
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

class AccountServiceImplTest {

    private val accountRepository: AccountRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val accountMapper: AccountMapper = mockk()
    private val accountNumberGenerator: AccountNumberGenerator = mockk()
    private val cardRepository: CardRepository = mockk()
    private val cardNumberGenerator: CardNumberGenerator = mockk()
    private val accountService = AccountServiceImpl(
        accountRepository, userRepository, accountMapper, accountNumberGenerator,
        cardRepository, cardNumberGenerator
    )

    private val now = OffsetDateTime.now()

    private fun userEntity(id: Long = 1L) = UserEntity(
        id = id,
        firstName = "Иван",
        lastName = "Иванов",
        phoneNumber = "+79991234567"
    )

    private fun accountEntity(
        id: Long = 1L,
        user: UserEntity = userEntity(),
        accountNumber: String = "1000000000000000001",
        accountType: AccountType = AccountType.CHECKING,
        balance: BigDecimal = BigDecimal("1000.0000")
    ) = AccountEntity(
        id = id,
        user = user,
        accountNumber = accountNumber,
        accountType = accountType,
        balance = balance
    )

    private fun accountResponse(
        id: Long = 1L,
        userId: Long = 1L,
        accountNumber: String = "1000000000000000001",
        accountType: String = "CHECKING",
        balance: BigDecimal = BigDecimal("1000.0000"),
        cardNumber: String? = null
    ) = AccountResponse(
        id = id,
        userId = userId,
        accountNumber = accountNumber,
        accountType = accountType,
        currency = "RUB",
        balance = balance,
        isActive = true,
        createdAt = now,
        cardNumber = cardNumber
    )

    // ===== createAccount =====

    @Test
    fun `createAccount - CHECKING creates card`() {
        val user = userEntity()
        val expected = accountResponse(cardNumber = "4200000000000001")

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { accountNumberGenerator.generate(AccountType.CHECKING) } returns "1000000000000000001"
        every { accountRepository.save(any()) } answers { firstArg() }
        every { cardNumberGenerator.generate() } returns "4200000000000001"
        every { cardRepository.save(any()) } answers { firstArg() }
        every { accountMapper.toResponse(any()) } returns expected

        val result = accountService.createAccount(1L, AccountType.CHECKING, "RUB")

        assertEquals(expected, result)
        verify { userRepository.findById(1L) }
        verify { accountNumberGenerator.generate(AccountType.CHECKING) }
        verify { accountRepository.save(any()) }
        verify { cardNumberGenerator.generate() }
        verify { cardRepository.save(any()) }
    }

    @Test
    fun `createAccount - SAVINGS does not create card`() {
        val user = userEntity()
        val expected = accountResponse(
            accountNumber = "2000000000000000001",
            accountType = "SAVINGS",
            cardNumber = null
        )

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { accountNumberGenerator.generate(AccountType.SAVINGS) } returns "2000000000000000001"
        every { accountRepository.save(any()) } answers { firstArg() }
        every { accountMapper.toResponse(any()) } returns expected

        val result = accountService.createAccount(1L, AccountType.SAVINGS, "RUB")

        assertEquals(expected, result)
        verify(exactly = 0) { cardNumberGenerator.generate() }
        verify(exactly = 0) { cardRepository.save(any()) }
    }

    @Test
    fun `createAccount - user not found throws NOT_FOUND`() {
        every { userRepository.findById(99L) } returns Optional.empty()

        val ex = assertThrows<BusinessException> {
            accountService.createAccount(99L, AccountType.CHECKING, "RUB")
        }

        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
        verify(exactly = 0) { accountRepository.save(any()) }
    }

    // ===== getAccount =====

    @Test
    fun `getAccount - success`() {
        val entity = accountEntity()
        val expected = accountResponse()

        every { accountRepository.findByAccountNumber("1000000000000000001") } returns entity
        every { accountMapper.toResponse(entity) } returns expected

        val result = accountService.getAccount("1000000000000000001")

        assertEquals(expected, result)
    }

    @Test
    fun `getAccount - not found throws NOT_FOUND`() {
        every { accountRepository.findByAccountNumber("9999999999999999999") } returns null

        val ex = assertThrows<BusinessException> {
            accountService.getAccount("9999999999999999999")
        }

        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    // ===== getAccountsByUser =====

    @Test
    fun `getAccountsByUser - returns list of accounts`() {
        val entity1 = accountEntity(id = 1L)
        val entity2 = accountEntity(id = 2L, accountNumber = "2000000000000000001", accountType = AccountType.SAVINGS)
        val response1 = accountResponse(id = 1L)
        val response2 = accountResponse(id = 2L, accountNumber = "2000000000000000001", accountType = "SAVINGS")

        every { accountRepository.findAllByUserId(1L) } returns listOf(entity1, entity2)
        every { accountMapper.toResponse(entity1) } returns response1
        every { accountMapper.toResponse(entity2) } returns response2

        val result = accountService.getAccountsByUser(1L)

        assertEquals(2, result.size)
        assertEquals(response1, result[0])
        assertEquals(response2, result[1])
    }

    @Test
    fun `getAccountsByUser - returns empty list when no accounts`() {
        every { accountRepository.findAllByUserId(99L) } returns emptyList()

        val result = accountService.getAccountsByUser(99L)

        assertEquals(0, result.size)
    }
}
