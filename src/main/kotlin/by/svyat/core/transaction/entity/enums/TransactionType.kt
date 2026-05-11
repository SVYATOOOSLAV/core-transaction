package by.svyat.core.transaction.entity.enums

/**
 * Тип банковской транзакции.
 *
 * Определяет бизнес-операцию и логику списания/зачисления средств.
 * Хранится в колонке `transaction_type` таблицы `transactions`.
 *
 * **Двусторонние операции** (дебет + кредит):
 * - [TRANSFER_SAVINGS], [TRANSFER_DEPOSIT], [TRANSFER_BROKERAGE] — внутренние переводы между CHECKING
 *   и соответствующим счётом в любую сторону
 * - [TRANSFER_CHECKING] — перевод между двумя расчётными счетами по номерам счетов
 * - [INTERBANK_TRANSFER] — межбанковский перевод по номеру карты
 * - [SBP_TRANSFER] — перевод через СБП по номеру телефона
 *
 * **Односторонние операции** (только кредит):
 * - [MONEY_GIFT] — зачисление на счёт без списания с другого
 */
enum class TransactionType {

    /** Перевод между CHECKING и сберегательным счётом в любую сторону (endpoint `POST /savings`) */
    TRANSFER_SAVINGS,

    /** Перевод между CHECKING и депозитным счётом в любую сторону (endpoint `POST /deposit`) */
    TRANSFER_DEPOSIT,

    /** Перевод между CHECKING и брокерским счётом в любую сторону (endpoint `POST /brokerage`) */
    TRANSFER_BROKERAGE,

    /** Перевод между двумя расчётными счетами (endpoint `POST /checking`) */
    TRANSFER_CHECKING,

    /** Межбанковский перевод по номерам карт (endpoint `POST /interbank`) */
    INTERBANK_TRANSFER,

    /** Денежный подарок — зачисление без списания (endpoint `POST /gift`) */
    MONEY_GIFT,

    /** Перевод через Систему быстрых платежей по номеру телефона (endpoint `POST /sbp`) */
    SBP_TRANSFER
}
