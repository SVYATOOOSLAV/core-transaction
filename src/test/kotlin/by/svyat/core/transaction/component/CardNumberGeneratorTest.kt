package by.svyat.core.transaction.component

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

class CardNumberGeneratorTest {

    private val jdbcTemplate: JdbcTemplate = mockk()
    private val generator = CardNumberGenerator(jdbcTemplate)

    @Test
    fun `generate - returns 16-digit number starting with 4200`() {
        every { jdbcTemplate.queryForObject("SELECT nextval('seq_card')", Long::class.java) } returns 1L

        val result = generator.generate()

        assertEquals(16, result.length)
        assertTrue(result.startsWith("4200"))
        assertTrue(result.all { it.isDigit() })
        assertEquals("4200000000000001", result)
    }

    @Test
    fun `generate - pads sequence correctly`() {
        every { jdbcTemplate.queryForObject("SELECT nextval('seq_card')", Long::class.java) } returns 42L

        val result = generator.generate()

        assertEquals("4200000000000042", result)
    }

    @Test
    fun `generate - large sequence value`() {
        every { jdbcTemplate.queryForObject("SELECT nextval('seq_card')", Long::class.java) } returns 123456789012L

        val result = generator.generate()

        assertEquals(16, result.length)
        assertEquals("4200123456789012", result)
    }
}
