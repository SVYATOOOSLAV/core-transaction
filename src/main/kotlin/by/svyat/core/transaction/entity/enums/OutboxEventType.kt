package by.svyat.core.transaction.entity.enums

enum class OutboxEventType {
    TRANSFER_COMPLETED,
    TRANSFER_FAILED,
    BALANCE_CHANGED,
    ACCOUNT_CREATED,
    ACCOUNT_DEACTIVATED
}
