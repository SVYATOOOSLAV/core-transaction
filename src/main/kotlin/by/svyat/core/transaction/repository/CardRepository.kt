package by.svyat.core.transaction.repository

import org.springframework.data.jpa.repository.JpaRepository
import by.svyat.core.transaction.entity.CardEntity

interface CardRepository : JpaRepository<CardEntity, Long> {
    fun findByCardNumber(cardNumber: String): CardEntity?
}
