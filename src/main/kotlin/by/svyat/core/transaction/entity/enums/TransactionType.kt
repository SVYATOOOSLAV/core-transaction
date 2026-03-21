package by.svyat.core.transaction.entity.enums

/**
 * Тип банковской транзакции.
 *
 * Определяет бизнес-операцию и логику списания/зачисления средств.
 * Хранится в колонке `transaction_type` таблицы `transactions`.
 *
 * **Двусторонние операции** (дебет + кредит):
 * - [TRANSFER_SAVINGS], [TRANSFER_DEPOSIT], [TRANSFER_BROKERAGE] — внутренние переводы между своими счетами
 * - [INTERBANK_TRANSFER] — межбанковский перевод по номеру карты
 * - [SBP_TRANSFER] — перевод через СБП по номеру телефона
 *
 * **Односторонние операции** (только кредит):
 * - [MONEY_GIFT], [COMPENSATION], [CREDIT_PAYMENT] — зачисление на счёт без списания с другого
 */
enum class TransactionType {

    /** Перевод с расчётного на сберегательный счёт (endpoint `POST /savings`) */
    TRANSFER_SAVINGS,

    /** Перевод с расчётного на депозитный счёт (endpoint `POST /deposit`) */
    TRANSFER_DEPOSIT,

    /** Перевод с расчётного на брокерский счёт (endpoint `POST /brokerage`) */
    TRANSFER_BROKERAGE,

    /** Межбанковский перевод по номерам карт (endpoint `POST /interbank`) */
    INTERBANK_TRANSFER,

    /** Денежный подарок — зачисление без списания (endpoint `POST /gift`) */
    MONEY_GIFT,

    /** Компенсация — зачисление без списания (endpoint `POST /compensation`) */
    COMPENSATION,

    /** Кредитный платёж — зачисление без списания (endpoint `POST /credit-payment`) */
    CREDIT_PAYMENT,

    /** Перевод через Систему быстрых платежей по номеру телефона (endpoint `POST /sbp`) */
    SBP_TRANSFER
}
