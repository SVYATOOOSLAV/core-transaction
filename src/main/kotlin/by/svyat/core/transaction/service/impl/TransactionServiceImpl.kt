package by.svyat.core.transaction.service.impl

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import by.svyat.core.transaction.api.common.BusinessException
import by.svyat.core.transaction.api.dto.request.CompensationRequest
import by.svyat.core.transaction.api.dto.request.CreditPaymentRequest
import by.svyat.core.transaction.api.dto.request.InterbankTransferRequest
import by.svyat.core.transaction.api.dto.request.MoneyGiftRequest
import by.svyat.core.transaction.api.dto.request.SbpTransferRequest
import by.svyat.core.transaction.api.dto.request.TransferRequest
import by.svyat.core.transaction.api.dto.response.TransactionResponse
import by.svyat.core.transaction.entity.AccountEntity
import by.svyat.core.transaction.entity.TransactionEntity
import by.svyat.core.transaction.entity.enums.AccountType
import by.svyat.core.transaction.entity.enums.TransactionStatus
import by.svyat.core.transaction.entity.enums.TransactionType
import by.svyat.core.transaction.mapping.TransactionMapper
import by.svyat.core.transaction.repository.AccountRepository
import by.svyat.core.transaction.repository.CardRepository
import by.svyat.core.transaction.repository.TransactionRepository
import by.svyat.core.transaction.repository.UserRepository
import by.svyat.core.transaction.service.TransactionService
import io.micrometer.core.instrument.Tag
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

private val log = KotlinLogging.logger {}

@Service
class TransactionServiceImpl(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val cardRepository: CardRepository,
    private val userRepository: UserRepository,
    private val transactionMapper: TransactionMapper,
    private val meterRegistry: MeterRegistry
) : TransactionService {

    private fun txCounter(type: TransactionType, status: String): Counter =
        Counter.builder("transactions.total")
            .tag("type", type.name)
            .tag("status", status)
            .description("Total number of transactions")
            .register(meterRegistry)

    private fun txTimer(type: TransactionType): Timer =
        Timer.builder("transactions.duration")
            .tag("type", type.name)
            .description("Transaction processing duration")
            .register(meterRegistry)

    @Transactional
    override fun transferToSavings(request: TransferRequest): TransactionResponse {
        return executeInternalTransfer(request, TransactionType.TRANSFER_SAVINGS, AccountType.SAVINGS)
    }

    @Transactional
    override fun transferToDeposit(request: TransferRequest): TransactionResponse {
        return executeInternalTransfer(request, TransactionType.TRANSFER_DEPOSIT, AccountType.DEPOSIT)
    }

    @Transactional
    override fun transferToBrokerage(request: TransferRequest): TransactionResponse {
        return executeInternalTransfer(request, TransactionType.TRANSFER_BROKERAGE, AccountType.BROKERAGE)
    }

    @Transactional
    override fun interbankTransfer(request: InterbankTransferRequest): TransactionResponse {
        return txTimer(TransactionType.INTERBANK_TRANSFER).recordCallable {
            log.info { "Interbank transfer: key=${request.idempotencyKey}, srcCard=${request.sourceCardNumber}, dstCard=${request.destinationCardNumber}, amount=${request.amount}" }

            checkIdempotency(request.idempotencyKey)?.let {
                log.info { "Idempotent hit for key=${request.idempotencyKey}" }
                return@recordCallable it
            }

            val sourceCard = cardRepository.findByCardNumber(request.sourceCardNumber)
                ?: throw BusinessException(HttpStatus.NOT_FOUND, "Source card ${request.sourceCardNumber} not found")
            val destCard = cardRepository.findByCardNumber(request.destinationCardNumber)
                ?: throw BusinessException(HttpStatus.NOT_FOUND, "Destination card ${request.destinationCardNumber} not found")

            if (!sourceCard.isActive) {
                throw BusinessException(HttpStatus.BAD_REQUEST, "Source card is inactive")
            }
            if (!destCard.isActive) {
                throw BusinessException(HttpStatus.BAD_REQUEST, "Destination card is inactive")
            }

            val sourceAccount = lockAndValidateSource(sourceCard.account.id, request.amount)
            val destAccount = accountRepository.findById(destCard.account.id)
                .orElseThrow { BusinessException(HttpStatus.NOT_FOUND, "Destination account not found") }

            executeDebitCredit(
                sourceAccount, destAccount, request.amount,
                TransactionType.INTERBANK_TRANSFER, request.idempotencyKey, request.description
            )
        }!!
    }

    @Transactional
    override fun sbpTransfer(request: SbpTransferRequest): TransactionResponse {
        return txTimer(TransactionType.SBP_TRANSFER).recordCallable {
            log.info { "SBP transfer: key=${request.idempotencyKey}, srcAccount=${request.sourceAccountId}, recipientPhone=${request.recipientPhoneNumber}, amount=${request.amount}" }

            checkIdempotency(request.idempotencyKey)?.let {
                log.info { "Idempotent hit for key=${request.idempotencyKey}" }
                return@recordCallable it
            }

            val recipientUser = userRepository.findByPhoneNumber(request.recipientPhoneNumber)
                ?: throw BusinessException(
                    HttpStatus.NOT_FOUND,
                    "Recipient with phone ${request.recipientPhoneNumber} not found"
                )

            val destAccount = accountRepository.findByUserIdAndAccountType(recipientUser.id, AccountType.CHECKING)
                ?: throw BusinessException(HttpStatus.NOT_FOUND, "Recipient has no checking account")

            val sourceAccount = lockAndValidateSource(request.sourceAccountId, request.amount)

            executeDebitCredit(
                sourceAccount, destAccount, request.amount,
                TransactionType.SBP_TRANSFER, request.idempotencyKey, request.description
            )
        }!!
    }

    @Transactional
    override fun processMoneyGift(request: MoneyGiftRequest): TransactionResponse {
        return executeCreditOnly(
            request.idempotencyKey, request.destinationAccountId, request.amount,
            TransactionType.MONEY_GIFT, request.description
        )
    }

    @Transactional
    override fun processCompensation(request: CompensationRequest): TransactionResponse {
        return executeCreditOnly(
            request.idempotencyKey, request.destinationAccountId, request.amount,
            TransactionType.COMPENSATION, request.description
        )
    }

    @Transactional
    override fun processCreditPayment(request: CreditPaymentRequest): TransactionResponse {
        return executeCreditOnly(
            request.idempotencyKey, request.destinationAccountId, request.amount,
            TransactionType.CREDIT_PAYMENT, request.description
        )
    }

    @Transactional(readOnly = true)
    override fun getTransaction(id: Long): TransactionResponse {
        log.debug { "Fetching transaction id=$id" }
        val tx = transactionRepository.findById(id)
            .orElseThrow { BusinessException(HttpStatus.NOT_FOUND, "Transaction with id $id not found") }
        return transactionMapper.toResponse(tx)
    }

    @Transactional(readOnly = true)
    override fun getTransactionsByAccount(accountId: Long): List<TransactionResponse> {
        log.debug { "Fetching transactions for accountId=$accountId" }
        return transactionRepository
            .findAllBySourceAccountIdOrDestinationAccountId(accountId, accountId)
            .map { transactionMapper.toResponse(it) }
    }

    // --- Private helpers ---

    private fun executeInternalTransfer(
        request: TransferRequest,
        type: TransactionType,
        expectedDestType: AccountType
    ): TransactionResponse {
        return txTimer(type).recordCallable {
            log.info { "Internal transfer: key=${request.idempotencyKey}, type=$type, src=${request.sourceAccountId}, dst=${request.destinationAccountId}, amount=${request.amount}" }

            checkIdempotency(request.idempotencyKey)?.let {
                log.info { "Idempotent hit for key=${request.idempotencyKey}" }
                return@recordCallable it
            }

            val destAccount = accountRepository.findById(request.destinationAccountId)
                .orElseThrow {
                    BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Destination account ${request.destinationAccountId} not found"
                    )
                }

            if (destAccount.accountType != expectedDestType) {
                throw BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Destination account type must be ${expectedDestType.name}, but was ${destAccount.accountType.name}"
                )
            }

            val sourceAccount = lockAndValidateSource(request.sourceAccountId, request.amount)

            executeDebitCredit(
                sourceAccount, destAccount, request.amount, type, request.idempotencyKey, request.description
            )
        }!!
    }

    private fun executeCreditOnly(
        idempotencyKey: UUID,
        destinationAccountId: Long,
        amount: BigDecimal,
        type: TransactionType,
        description: String?
    ): TransactionResponse {
        return txTimer(type).recordCallable {
            log.info { "Credit-only: key=$idempotencyKey, type=$type, dst=$destinationAccountId, amount=$amount" }

            checkIdempotency(idempotencyKey)?.let {
                log.info { "Idempotent hit for key=$idempotencyKey" }
                return@recordCallable it
            }

            val destAccount = accountRepository.findById(destinationAccountId)
                .orElseThrow {
                    BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Destination account $destinationAccountId not found"
                    )
                }

            if (!destAccount.isActive) {
                throw BusinessException(HttpStatus.BAD_REQUEST, "Destination account is inactive")
            }

            destAccount.balance = destAccount.balance.add(amount)
            destAccount.updatedAt = OffsetDateTime.now()
            accountRepository.save(destAccount)

            val transaction = TransactionEntity(
                idempotencyKey = idempotencyKey,
                transactionType = type,
                status = TransactionStatus.COMPLETED,
                destinationAccount = destAccount,
                amount = amount,
                description = description,
                completedAt = OffsetDateTime.now()
            )
            val saved = transactionRepository.save(transaction)

            txCounter(type, "COMPLETED").increment()
            log.info { "Transaction completed: id=${saved.id}, type=$type, amount=$amount" }

            transactionMapper.toResponse(saved)
        }!!
    }

    private fun executeDebitCredit(
        source: AccountEntity,
        destination: AccountEntity,
        amount: BigDecimal,
        type: TransactionType,
        idempotencyKey: UUID,
        description: String?
    ): TransactionResponse {
        source.balance = source.balance.subtract(amount)
        source.updatedAt = OffsetDateTime.now()
        accountRepository.save(source)

        destination.balance = destination.balance.add(amount)
        destination.updatedAt = OffsetDateTime.now()
        accountRepository.save(destination)

        val transaction = TransactionEntity(
            idempotencyKey = idempotencyKey,
            transactionType = type,
            status = TransactionStatus.COMPLETED,
            sourceAccount = source,
            destinationAccount = destination,
            amount = amount,
            description = description,
            completedAt = OffsetDateTime.now()
        )
        val saved = transactionRepository.save(transaction)

        txCounter(type, "COMPLETED").increment()
        meterRegistry.gauge(
            "accounts.balance", listOf(
                Tag.of("accountId", source.id.toString())
            ), source.balance.toDouble()
        )
        log.info { "Transaction completed: id=${saved.id}, type=$type, src=${source.id}, dst=${destination.id}, amount=$amount" }

        return transactionMapper.toResponse(saved)
    }

    private fun lockAndValidateSource(accountId: Long, amount: BigDecimal): AccountEntity {
        val account = accountRepository.findByIdForUpdate(accountId)
            ?: throw BusinessException(HttpStatus.NOT_FOUND, "Source account $accountId not found")

        if (!account.isActive) {
            log.warn { "Source account $accountId is inactive" }
            throw BusinessException(HttpStatus.BAD_REQUEST, "Source account is inactive")
        }
        if (account.balance < amount) {
            log.warn { "Insufficient funds: accountId=$accountId, available=${account.balance}, requested=$amount" }
            txCounter(TransactionType.INTERBANK_TRANSFER, "FAILED").increment()
            throw BusinessException(
                HttpStatus.BAD_REQUEST,
                "Insufficient funds: available ${account.balance}, requested $amount"
            )
        }
        return account
    }

    private fun checkIdempotency(key: UUID): TransactionResponse? {
        return transactionRepository.findByIdempotencyKey(key)?.let {
            transactionMapper.toResponse(it)
        }
    }
}
