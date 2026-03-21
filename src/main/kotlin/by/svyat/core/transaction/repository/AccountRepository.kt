package by.svyat.core.transaction.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import by.svyat.core.transaction.entity.AccountEntity
import by.svyat.core.transaction.entity.enums.AccountType

interface AccountRepository : JpaRepository<AccountEntity, Long> {

    fun findByAccountNumber(accountNumber: String): AccountEntity?

    fun findAllByUserId(userId: Long): List<AccountEntity>

    fun findByUserIdAndAccountType(userId: Long, accountType: AccountType): AccountEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a WHERE a.accountNumber = :accountNumber")
    fun findByAccountNumberForUpdate(accountNumber: String): AccountEntity?
}
