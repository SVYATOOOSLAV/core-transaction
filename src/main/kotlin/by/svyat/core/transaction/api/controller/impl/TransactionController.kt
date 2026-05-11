package by.svyat.core.transaction.api.controller.impl

import by.svyat.core.transaction.api.controller.TransactionApi
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
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

    @PreAuthorize("hasRole('GATEWAY')")
    override fun transferToSavings(request: TransferRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.transferToSavings(request), HttpStatus.CREATED)
    }

    @PreAuthorize("hasRole('GATEWAY')")
    override fun transferToDeposit(request: TransferRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.transferToDeposit(request), HttpStatus.CREATED)
    }

    @PreAuthorize("hasRole('GATEWAY')")
    override fun transferToBrokerage(request: TransferRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.transferToBrokerage(request), HttpStatus.CREATED)
    }

    @PreAuthorize("hasRole('GATEWAY')")
    override fun transferToChecking(request: TransferRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.transferToChecking(request), HttpStatus.CREATED)
    }

    @PreAuthorize("hasRole('GATEWAY')")
    override fun interbankTransfer(request: InterbankTransferRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.interbankTransfer(request), HttpStatus.CREATED)
    }

    @PreAuthorize("hasRole('GATEWAY')")
    override fun sbpTransfer(request: SbpTransferRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.sbpTransfer(request), HttpStatus.CREATED)
    }

    @PreAuthorize("hasAnyRole('GATEWAY','SUPPORT')")
    override fun processMoneyGift(request: MoneyGiftRequest): ResponseEntity<TransactionResponse> {
        return ResponseEntity(transactionService.processMoneyGift(request), HttpStatus.CREATED)
    }

    @PreAuthorize("hasAnyRole('GATEWAY','SUPPORT')")
    override fun getTransaction(id: Long): ResponseEntity<TransactionResponse> {
        return ResponseEntity.ok(transactionService.getTransaction(id))
    }

    @PreAuthorize("hasAnyRole('GATEWAY','SUPPORT')")
    override fun getTransactionsByAccount(accountNumber: String): ResponseEntity<List<TransactionResponse>> {
        return ResponseEntity.ok(transactionService.getTransactionsByAccount(accountNumber))
    }
}
