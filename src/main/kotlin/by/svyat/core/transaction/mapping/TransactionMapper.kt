package by.svyat.core.transaction.mapping

import org.springframework.stereotype.Component
import by.svyat.core.transaction.api.dto.response.TransactionResponse
import by.svyat.core.transaction.entity.TransactionEntity

@Component
class TransactionMapper {

    fun toResponse(entity: TransactionEntity): TransactionResponse {
        return TransactionResponse(
            id = entity.id,
            idempotencyKey = entity.idempotencyKey,
            transactionType = entity.transactionType.name,
            status = entity.status.name,
            sourceAccountId = entity.sourceAccount?.id,
            destinationAccountId = entity.destinationAccount?.id,
            amount = entity.amount,
            currency = entity.currency,
            description = entity.description,
            errorMessage = entity.errorMessage,
            createdAt = entity.createdAt,
            completedAt = entity.completedAt
        )
    }
}
