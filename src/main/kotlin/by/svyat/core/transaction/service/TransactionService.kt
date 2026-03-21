package by.svyat.core.transaction.service

import by.svyat.core.transaction.api.dto.request.CompensationRequest
import by.svyat.core.transaction.api.dto.request.CreditPaymentRequest
import by.svyat.core.transaction.api.dto.request.InterbankTransferRequest
import by.svyat.core.transaction.api.dto.request.MoneyGiftRequest
import by.svyat.core.transaction.api.dto.request.SbpTransferRequest
import by.svyat.core.transaction.api.dto.request.TransferRequest
import by.svyat.core.transaction.api.dto.response.TransactionResponse

interface TransactionService {
    fun transferToSavings(request: TransferRequest): TransactionResponse
    fun transferToDeposit(request: TransferRequest): TransactionResponse
    fun transferToBrokerage(request: TransferRequest): TransactionResponse
    fun interbankTransfer(request: InterbankTransferRequest): TransactionResponse
    fun sbpTransfer(request: SbpTransferRequest): TransactionResponse
    fun processMoneyGift(request: MoneyGiftRequest): TransactionResponse
    fun processCompensation(request: CompensationRequest): TransactionResponse
    fun processCreditPayment(request: CreditPaymentRequest): TransactionResponse
    fun getTransaction(id: Long): TransactionResponse
    fun getTransactionsByAccount(accountId: Long): List<TransactionResponse>
}
