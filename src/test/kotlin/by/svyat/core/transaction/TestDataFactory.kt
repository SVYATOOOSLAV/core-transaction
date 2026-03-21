package by.svyat.core.transaction

import by.svyat.core.transaction.api.dto.request.CompensationRequest
import by.svyat.core.transaction.api.dto.request.CreateAccountRequest
import by.svyat.core.transaction.api.dto.request.CreateUserRequest
import by.svyat.core.transaction.api.dto.request.CreditPaymentRequest
import by.svyat.core.transaction.api.dto.request.InterbankTransferRequest
import by.svyat.core.transaction.api.dto.request.MoneyGiftRequest
import by.svyat.core.transaction.api.dto.request.SbpTransferRequest
import by.svyat.core.transaction.api.dto.request.TransferRequest
import java.math.BigDecimal
import java.util.*

object TestDataFactory {

    fun userRequest(
        firstName: String = "Иван",
        lastName: String = "Иванов",
        patronymic: String? = null,
        phoneNumber: String = "+79991234567",
        email: String? = null
    ) = CreateUserRequest(firstName, lastName, patronymic, phoneNumber, email)

    fun accountRequest(
        userId: Long,
        accountNumber: String = "40817810000000000001",
        accountType: String = "CHECKING",
        currency: String = "RUB"
    ) = CreateAccountRequest(userId, accountNumber, accountType, currency)

    fun transferRequest(
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: BigDecimal = BigDecimal("500.00"),
        description: String? = null,
        idempotencyKey: UUID = UUID.randomUUID()
    ) = TransferRequest(idempotencyKey, sourceAccountId, destinationAccountId, amount, description)

    fun moneyGiftRequest(
        destinationAccountId: Long,
        amount: BigDecimal = BigDecimal("1000.00"),
        description: String? = null,
        idempotencyKey: UUID = UUID.randomUUID()
    ) = MoneyGiftRequest(idempotencyKey, destinationAccountId, amount, description)

    fun compensationRequest(
        destinationAccountId: Long,
        amount: BigDecimal = BigDecimal("500.00"),
        description: String? = null,
        idempotencyKey: UUID = UUID.randomUUID()
    ) = CompensationRequest(idempotencyKey, destinationAccountId, amount, description)

    fun creditPaymentRequest(
        destinationAccountId: Long,
        amount: BigDecimal = BigDecimal("300.00"),
        description: String? = null,
        idempotencyKey: UUID = UUID.randomUUID()
    ) = CreditPaymentRequest(idempotencyKey, destinationAccountId, amount, description)

    fun interbankTransferRequest(
        sourceCardNumber: String,
        destinationCardNumber: String,
        amount: BigDecimal = BigDecimal("500.00"),
        description: String? = null,
        idempotencyKey: UUID = UUID.randomUUID()
    ) = InterbankTransferRequest(idempotencyKey, sourceCardNumber, destinationCardNumber, amount, description)

    fun sbpTransferRequest(
        sourceAccountId: Long,
        recipientPhoneNumber: String,
        amount: BigDecimal = BigDecimal("500.00"),
        description: String? = null,
        idempotencyKey: UUID = UUID.randomUUID()
    ) = SbpTransferRequest(idempotencyKey, sourceAccountId, recipientPhoneNumber, amount, description)
}
