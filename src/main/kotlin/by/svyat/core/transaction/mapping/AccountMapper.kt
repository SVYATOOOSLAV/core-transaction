package by.svyat.core.transaction.mapping

import org.springframework.stereotype.Component
import by.svyat.core.transaction.api.dto.response.AccountResponse
import by.svyat.core.transaction.entity.AccountEntity

@Component
class AccountMapper {

    fun toResponse(entity: AccountEntity): AccountResponse {
        return AccountResponse(
            id = entity.id,
            userId = entity.user.id,
            accountNumber = entity.accountNumber,
            accountType = entity.accountType.name,
            currency = entity.currency,
            balance = entity.balance,
            isActive = entity.isActive,
            createdAt = entity.createdAt,
            cardNumber = entity.cards.firstOrNull()?.cardNumber
        )
    }
}
