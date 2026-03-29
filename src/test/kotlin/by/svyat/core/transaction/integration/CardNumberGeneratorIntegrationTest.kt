package by.svyat.core.transaction.integration

import by.svyat.core.transaction.IntegrationTestBase
import by.svyat.core.transaction.component.CardNumberGenerator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CardNumberGeneratorIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var generator: CardNumberGenerator

    @Nested
    inner class Format {

        @Test
        fun `card number starts with 4200`() {
            val number = generator.generate()
            assertTrue(number.startsWith("4200")) { "Expected prefix '4200', got: $number" }
        }

        @Test
        fun `card number is exactly 16 characters`() {
            val number = generator.generate()
            assertEquals(16, number.length) { "Expected 16 chars, got ${number.length}: $number" }
        }

        @Test
        fun `card number contains only digits`() {
            val number = generator.generate()
            assertTrue(number.all { it.isDigit() }) { "Expected only digits, got: $number" }
        }
    }

    @Nested
    inner class Uniqueness {

        @Test
        fun `sequential calls produce unique numbers`() {
            val numbers = (1..10).map { generator.generate() }
            assertEquals(10, numbers.toSet().size) { "Expected 10 unique numbers, got duplicates: $numbers" }
        }

        @Test
        fun `sequential calls produce incrementing sequence values`() {
            val numbers = (1..5).map { generator.generate() }
            val sequenceValues = numbers.map { it.substring(4).toLong() }
            assertEquals(sequenceValues.sorted(), sequenceValues) { "Sequence values should be monotonically increasing" }
        }
    }
}
