package by.svyat.core.transaction.service

import by.svyat.core.transaction.api.dto.response.AccountResponse
import by.svyat.core.transaction.entity.enums.AccountType

interface AccountService {
    fun createAccount(userId: Long, accountNumber: String, accountType: AccountType, currency: String): AccountResponse
    fun getAccount(id: Long): AccountResponse
    fun getAccountsByUser(userId: Long): List<AccountResponse>
}
