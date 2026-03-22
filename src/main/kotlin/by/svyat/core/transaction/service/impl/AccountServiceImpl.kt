package by.svyat.core.transaction.service.impl

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import by.svyat.core.transaction.api.common.BusinessException
import by.svyat.core.transaction.api.dto.response.AccountResponse
import by.svyat.core.transaction.entity.AccountEntity
import by.svyat.core.transaction.entity.enums.AccountType
import by.svyat.core.transaction.mapping.AccountMapper
import by.svyat.core.transaction.repository.AccountRepository
import by.svyat.core.transaction.repository.UserRepository
import by.svyat.core.transaction.component.AccountNumberGenerator
import by.svyat.core.transaction.service.AccountService

private val log = KotlinLogging.logger {}

@Service
class AccountServiceImpl(
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val accountMapper: AccountMapper,
    private val accountNumberGenerator: AccountNumberGenerator
) : AccountService {

    @Transactional
    override fun createAccount(
        userId: Long,
        accountType: AccountType,
        currency: String
    ): AccountResponse {
        log.info { "Creating account: userId=$userId, type=$accountType" }

        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(HttpStatus.NOT_FOUND, "User with id $userId not found") }

        val accountNumber = accountNumberGenerator.generate(accountType)

        val account = AccountEntity(
            user = user,
            accountNumber = accountNumber,
            accountType = accountType,
            currency = currency
        )
        val saved = accountRepository.save(account)
        log.info { "Account created: id=${saved.id}, number=$accountNumber" }
        return accountMapper.toResponse(saved)
    }

    @Transactional(readOnly = true)
    override fun getAccount(accountNumber: String): AccountResponse {
        log.debug { "Fetching account by accountNumber=$accountNumber" }
        val account = accountRepository.findByAccountNumber(accountNumber)
            ?: throw BusinessException(HttpStatus.NOT_FOUND, "Account with number $accountNumber not found")
        return accountMapper.toResponse(account)
    }

    @Transactional(readOnly = true)
    override fun getAccountsByUser(userId: Long): List<AccountResponse> {
        log.debug { "Fetching accounts for userId=$userId" }
        return accountRepository.findAllByUserId(userId).map { accountMapper.toResponse(it) }
    }
}
