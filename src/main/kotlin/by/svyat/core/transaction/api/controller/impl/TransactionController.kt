package by.svyat.core.transaction.api.controller.impl

import by.svyat.core.transaction.api.controller.TransactionApi
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import by.svyat.core.transaction.api.dto.request.CompensationRequest
import by.svyat.core.transaction.api.dto.request.CreditPaymentRequest
import by.svyat.core.transaction.api.dto.request.InterbankTransferRequest
import by.svyat.core.transaction.api.dto.request.MoneyGiftRequest
import by.svyat.core.transaction.api.dto.request.SbpTransferRequest
import by.svyat.core.transaction.api.dto.request.TransferRequest
import by.svyat.core.transaction.api.dto.response.TransactionResponse
import by.svyat.core.transaction.service.TransactionService

@RestController
class TransactionController(
    private val transactionService: TransactionService
) : TransactionApi {

    override fun transferToSavings(request: TransferRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.transferToSavings(request), HttpStatus.CREATED)
    }

    override fun transferToDeposit(request: TransferRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.transferToDeposit(request), HttpStatus.CREATED)
    }

    override fun transferToBrokerage(request: TransferRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.transferToBrokerage(request), HttpStatus.CREATED)
    }

    override fun interbankTransfer(request: InterbankTransferRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.interbankTransfer(request), HttpStatus.CREATED)
    }

    override fun sbpTransfer(request: SbpTransferRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.sbpTransfer(request), HttpStatus.CREATED)
    }

    override fun processMoneyGift(request: MoneyGiftRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.processMoneyGift(request), HttpStatus.CREATED)
    }

    override fun processCompensation(request: CompensationRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.processCompensation(request), HttpStatus.CREATED)
    }

    override fun processCreditPayment(request: CreditPaymentRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.processCreditPayment(request), HttpStatus.CREATED)
    }

    override fun getTransaction(id: Long): ResponseEntity<TransactionResponse> {
        return ResponseEntity.ok(transactionService.getTransaction(id))
    }

    override fun getTransactionsByAccount(accountId: Long): ResponseEntity<List<TransactionResponse>> {
        return ResponseEntity.ok(transactionService.getTransactionsByAccount(accountId))
    }
}
