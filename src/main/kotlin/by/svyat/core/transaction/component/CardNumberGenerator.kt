package by.svyat.core.transaction.component

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class CardNumberGenerator(
    private val jdbcTemplate: JdbcTemplate
) {

    companion object {
        const val PREFIX = "4200"
        private const val CARD_LENGTH = 16
    }

    fun generate(): String {
        val sequence = jdbcTemplate.queryForObject(
            "SELECT nextval('seq_card')",
            Long::class.java
        )!!

        return PREFIX + sequence.toString().padStart(CARD_LENGTH - PREFIX.length, '0')
    }
}
