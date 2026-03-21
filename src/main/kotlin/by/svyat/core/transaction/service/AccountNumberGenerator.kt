package by.svyat.core.transaction.service

import by.svyat.core.transaction.entity.enums.AccountType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class AccountNumberGenerator(
    private val jdbcTemplate: JdbcTemplate
) {

    fun generate(accountType: AccountType): String {
        val sequence = jdbcTemplate.queryForObject(
            prepareSql(prepareSequenceByAccountType(accountType)),
            Long::class.java
        )!!

        return accountType.prefix + sequence.toString().padStart(19, '0')
    }

    private fun prepareSequenceByAccountType(accountType: AccountType) =
        "seq_account_${accountType.name.lowercase()}"

    private fun prepareSql(sequenceName: String) = "SELECT nextval('$sequenceName')"
}
