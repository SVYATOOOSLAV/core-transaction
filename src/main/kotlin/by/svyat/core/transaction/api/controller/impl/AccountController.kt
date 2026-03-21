package by.svyat.core.transaction.api.controller.impl

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import by.svyat.core.transaction.api.common.BusinessException
import by.svyat.core.transaction.api.controller.AccountApi
import by.svyat.core.transaction.api.dto.request.CreateAccountRequest
import by.svyat.core.transaction.api.dto.response.AccountResponse
import by.svyat.core.transaction.entity.enums.AccountType
import by.svyat.core.transaction.service.AccountService

@RestController
class AccountController(
    private val accountService: AccountService
) : AccountApi {

    override fun createAccount(request: CreateAccountRequest): ResponseEntity<AccountResponse> {
        val accountType = try {
            AccountType.valueOf(request.accountType.uppercase())
        } catch (e: IllegalArgumentException) {
            throw BusinessException(
                HttpStatus.BAD_REQUEST,
                "Invalid account type: ${request.accountType}. Valid types: ${AccountType.entries.joinToString()}"
            )
        }

        val response = accountService.createAccount(
            userId = request.userId,
            accountNumber = request.accountNumber,
            accountType = accountType,
            currency = request.currency
        )

        return ResponseEntity(response, HttpStatus.CREATED)
    }

    override fun getAccount(id: Long): ResponseEntity<AccountResponse> {
        return ResponseEntity.ok(accountService.getAccount(id))
    }

    override fun getAccountsByUser(userId: Long): ResponseEntity<List<AccountResponse>> {
        return ResponseEntity.ok(accountService.getAccountsByUser(userId))
    }
}
