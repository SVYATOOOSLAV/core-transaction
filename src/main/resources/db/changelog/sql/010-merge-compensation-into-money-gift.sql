-- Объединяем COMPENSATION с MONEY_GIFT: исторические записи переводим в MONEY_GIFT
-- и убираем COMPENSATION из CHECK-ограничения, поскольку API/enum его больше не поддерживают.

UPDATE transactions
SET transaction_type = 'MONEY_GIFT'
WHERE transaction_type = 'COMPENSATION';

ALTER TABLE transactions DROP CONSTRAINT chk_transaction_type;

ALTER TABLE transactions ADD CONSTRAINT chk_transaction_type
    CHECK (transaction_type IN (
        'TRANSFER_SAVINGS', 'TRANSFER_DEPOSIT', 'TRANSFER_BROKERAGE',
        'INTERBANK_TRANSFER', 'MONEY_GIFT',
        'CREDIT_PAYMENT', 'SBP_TRANSFER'
    ));
