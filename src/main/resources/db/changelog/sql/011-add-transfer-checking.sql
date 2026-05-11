-- Расширяем CHECK-ограничение типов транзакций: добавляем TRANSFER_CHECKING
-- для нового эндпоинта POST /api/v1/transactions/checking (перевод между двумя CHECKING-счетами).

ALTER TABLE transactions DROP CONSTRAINT chk_transaction_type;

ALTER TABLE transactions ADD CONSTRAINT chk_transaction_type
    CHECK (transaction_type IN (
        'TRANSFER_SAVINGS', 'TRANSFER_DEPOSIT', 'TRANSFER_BROKERAGE',
        'TRANSFER_CHECKING', 'INTERBANK_TRANSFER', 'MONEY_GIFT',
        'CREDIT_PAYMENT', 'SBP_TRANSFER'
    ));
