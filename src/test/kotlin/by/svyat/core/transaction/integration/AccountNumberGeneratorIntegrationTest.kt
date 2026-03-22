package by.svyat.core.transaction.integration

import by.svyat.core.transaction.IntegrationTestBase
import by.svyat.core.transaction.entity.enums.AccountType
import by.svyat.core.transaction.component.AccountNumberGenerator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AccountNumberGeneratorIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var generator: AccountNumberGenerator

    @Nested
    inner class Prefix {

        @Test
        fun `CHECKING account number starts with 1`() {
            val number = generator.generate(AccountType.CHECKING)
            assertTrue(number.startsWith("1")) { "Expected prefix '1', got: $number" }
        }

        @Test
        fun `SAVINGS account number starts with 2`() {
            val number = generator.generate(AccountType.SAVINGS)
            assertTrue(number.startsWith("2")) { "Expected prefix '2', got: $number" }
        }

        @Test
        fun `DEPOSIT account number starts with 3`() {
            val number = generator.generate(AccountType.DEPOSIT)
            assertTrue(number.startsWith("3")) { "Expected prefix '3', got: $number" }
        }

        @Test
        fun `BROKERAGE account number starts with 4`() {
            val number = generator.generate(AccountType.BROKERAGE)
            assertTrue(number.startsWith("4")) { "Expected prefix '4', got: $number" }
        }
    }

    @Nested
    inner class Format {

        @Test
        fun `account number is exactly 20 characters`() {
            AccountType.entries.forEach { type ->
                val number = generator.generate(type)
                assertEquals(20, number.length) { "Expected 20 chars for $type, got ${number.length}: $number" }
            }
        }

        @Test
        fun `account number contains only digits`() {
            AccountType.entries.forEach { type ->
                val number = generator.generate(type)
                assertTrue(number.all { it.isDigit() }) { "Expected only digits for $type, got: $number" }
            }
        }
    }

    @Nested
    inner class Uniqueness {

        @Test
        fun `sequential calls produce unique numbers`() {
            val numbers = (1..10).map { generator.generate(AccountType.CHECKING) }
            assertEquals(10, numbers.toSet().size) { "Expected 10 unique numbers, got duplicates: $numbers" }
        }

        @Test
        fun `sequential calls produce incrementing sequence values`() {
            val numbers = (1..5).map { generator.generate(AccountType.SAVINGS) }
            val sequenceValues = numbers.map { it.substring(1).toLong() }
            assertEquals(sequenceValues.sorted(), sequenceValues) { "Sequence values should be monotonically increasing" }
        }
    }

    @Nested
    inner class IndependentSequences {

        @Test
        fun `different account types use independent sequences`() {
            val checking1 = generator.generate(AccountType.CHECKING)
            val savings1 = generator.generate(AccountType.SAVINGS)
            val checking2 = generator.generate(AccountType.CHECKING)
            val savings2 = generator.generate(AccountType.SAVINGS)

            val checkingSeq1 = checking1.substring(1).toLong()
            val checkingSeq2 = checking2.substring(1).toLong()
            val savingsSeq1 = savings1.substring(1).toLong()
            val savingsSeq2 = savings2.substring(1).toLong()

            assertEquals(1, checkingSeq2 - checkingSeq1) { "CHECKING sequence should increment by 1" }
            assertEquals(1, savingsSeq2 - savingsSeq1) { "SAVINGS sequence should increment by 1" }
        }
    }
}
