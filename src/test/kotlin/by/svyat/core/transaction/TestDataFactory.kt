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
        accountType: String = "CHECKING",
        currency: String = "RUB"
    ) = CreateAccountRequest(userId, accountType, currency)

    fun transferRequest(
        sourceAccountNumber: String,
        destinationAccountNumber: String,
        amount: BigDecimal = BigDecimal("500.00"),
        description: String? = null,
        idempotencyKey: UUID = UUID.randomUUID()
    ) = TransferRequest(idempotencyKey, sourceAccountNumber, destinationAccountNumber, amount, description)

    fun moneyGiftRequest(
        destinationAccountNumber: String,
        amount: BigDecimal = BigDecimal("1000.00"),
        description: String? = null,
        idempotencyKey: UUID = UUID.randomUUID()
    ) = MoneyGiftRequest(idempotencyKey, destinationAccountNumber, amount, description)

    fun compensationRequest(
        destinationAccountNumber: String,
        amount: BigDecimal = BigDecimal("500.00"),
        description: String? = null,
        idempotencyKey: UUID = UUID.randomUUID()
    ) = CompensationRequest(idempotencyKey, destinationAccountNumber, amount, description)

    fun creditPaymentRequest(
        sourceAccountNumber: String,
        destinationAccountNumber: String,
        amount: BigDecimal = BigDecimal("300.00"),
        description: String? = null,
        idempotencyKey: UUID = UUID.randomUUID()
    ) = CreditPaymentRequest(idempotencyKey, sourceAccountNumber, destinationAccountNumber, amount, description)

    fun interbankTransferRequest(
        sourceCardNumber: String,
        destinationCardNumber: String,
        amount: BigDecimal = BigDecimal("500.00"),
        description: String? = null,
        idempotencyKey: UUID = UUID.randomUUID()
    ) = InterbankTransferRequest(idempotencyKey, sourceCardNumber, destinationCardNumber, amount, description)

    fun sbpTransferRequest(
        sourceAccountNumber: String,
        recipientPhoneNumber: String,
        amount: BigDecimal = BigDecimal("500.00"),
        description: String? = null,
        idempotencyKey: UUID = UUID.randomUUID()
    ) = SbpTransferRequest(idempotencyKey, sourceAccountNumber, recipientPhoneNumber, amount, description)
}
