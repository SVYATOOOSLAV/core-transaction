package by.svyat.core.transaction.api.controller

import by.svyat.core.transaction.api.dto.request.CompensationRequest
import by.svyat.core.transaction.api.dto.request.CreditPaymentRequest
import by.svyat.core.transaction.api.dto.request.InterbankTransferRequest
import by.svyat.core.transaction.api.dto.request.MoneyGiftRequest
import by.svyat.core.transaction.api.dto.request.SbpTransferRequest
import by.svyat.core.transaction.api.dto.request.TransferRequest
import by.svyat.core.transaction.api.dto.response.ErrorResponse
import by.svyat.core.transaction.api.dto.response.TransactionResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Tag(name = "Транзакции", description = "Операции с банковскими транзакциями")
@RequestMapping("/api/v1/transactions")
interface TransactionApi {

    @Operation(
        summary = "Перевод на сберегательный счёт",
        description = "Перевод средств с расчётного счёта на сберегательный счёт того же пользователя"
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Транзакция выполнена"),
        ApiResponse(
            responseCode = "400", description = "Ошибка валидации или недостаточно средств",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "404", description = "Счёт не найден",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409", description = "Дублирование идемпотентного ключа",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping("/savings")
    fun transferToSavings(@Valid @RequestBody request: TransferRequest): ResponseEntity<TransactionResponse>

    @Operation(
        summary = "Перевод на депозитный счёт",
        description = "Перевод средств с расчётного счёта на депозитный счёт того же пользователя"
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Транзакция выполнена"),
        ApiResponse(
            responseCode = "400", description = "Ошибка валидации или недостаточно средств",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "404", description = "Счёт не найден",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409", description = "Дублирование идемпотентного ключа",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping("/deposit")
    fun transferToDeposit(@Valid @RequestBody request: TransferRequest): ResponseEntity<TransactionResponse>

    @Operation(
        summary = "Перевод на брокерский счёт",
        description = "Перевод средств с расчётного счёта на брокерский счёт того же пользователя"
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Транзакция выполнена"),
        ApiResponse(
            responseCode = "400", description = "Ошибка валидации или недостаточно средств",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "404", description = "Счёт не найден",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409", description = "Дублирование идемпотентного ключа",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping("/brokerage")
    fun transferToBrokerage(@Valid @RequestBody request: TransferRequest): ResponseEntity<TransactionResponse>

    @Operation(
        summary = "Межбанковский перевод",
        description = "Перевод средств между картами разных банков по номеру карты"
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Транзакция выполнена"),
        ApiResponse(
            responseCode = "400", description = "Ошибка валидации или недостаточно средств",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "404", description = "Карта не найдена",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409", description = "Дублирование идемпотентного ключа",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping("/interbank")
    fun interbankTransfer(@Valid @RequestBody request: InterbankTransferRequest): ResponseEntity<TransactionResponse>

    @Operation(
        summary = "Перевод через СБП",
        description = "Перевод средств по номеру телефона через Систему быстрых платежей"
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Транзакция выполнена"),
        ApiResponse(
            responseCode = "400", description = "Ошибка валидации или недостаточно средств",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "404", description = "Счёт или получатель не найден",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409", description = "Дублирование идемпотентного ключа",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping("/sbp")
    fun sbpTransfer(@Valid @RequestBody request: SbpTransferRequest): ResponseEntity<TransactionResponse>

    @Operation(
        summary = "Денежный подарок",
        description = "Зачисление денежного подарка на счёт получателя (только кредит, без дебета)"
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Транзакция выполнена"),
        ApiResponse(
            responseCode = "400", description = "Ошибка валидации",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "404", description = "Счёт не найден",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409", description = "Дублирование идемпотентного ключа",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping("/gift")
    fun processMoneyGift(@Valid @RequestBody request: MoneyGiftRequest): ResponseEntity<TransactionResponse>

    @Operation(
        summary = "Компенсация",
        description = "Зачисление компенсации на счёт получателя (только кредит, без дебета)"
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Транзакция выполнена"),
        ApiResponse(
            responseCode = "400", description = "Ошибка валидации",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "404", description = "Счёт не найден",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409", description = "Дублирование идемпотентного ключа",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping("/compensation")
    fun processCompensation(@Valid @RequestBody request: CompensationRequest): ResponseEntity<TransactionResponse>

    @Operation(
        summary = "Кредитный платёж",
        description = "Перевод средств со счёта источника на кредитный счёт (дебет источника, кредит получателя)"
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Транзакция выполнена"),
        ApiResponse(
            responseCode = "400", description = "Ошибка валидации",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "404", description = "Счёт не найден",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409", description = "Дублирование идемпотентного ключа",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @PostMapping("/credit-payment")
    fun processCreditPayment(@Valid @RequestBody request: CreditPaymentRequest): ResponseEntity<TransactionResponse>

    @Operation(summary = "Получить транзакцию по ID")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Транзакция найдена"),
        ApiResponse(
            responseCode = "404", description = "Транзакция не найдена",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    )
    @GetMapping("/{id}")
    fun getTransaction(
        @Parameter(description = "ID транзакции") @PathVariable id: Long
    ): ResponseEntity<TransactionResponse>

    @Operation(summary = "Получить транзакции по счёту")
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Список транзакций",
            content = [Content(array = ArraySchema(schema = Schema(implementation = TransactionResponse::class)))]
        )
    )
    @GetMapping("/account/{accountNumber}")
    fun getTransactionsByAccount(
        @Parameter(description = "Номер счёта") @PathVariable accountNumber: String
    ): ResponseEntity<List<TransactionResponse>>
}
