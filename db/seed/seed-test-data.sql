-- seed-test-data.sql
-- Наполнение transaction_db тестовыми данными:
--   * 100 000 users
--   * ~640 000 accounts (CHECKING 1-10 на юзера + по одному SAVINGS/DEPOSIT/BROKERAGE у ~30%)
--   * ~550 000 cards (по одной на каждый CHECKING-счёт)
--
-- Запуск:
--   PowerShell (Windows):
--     Get-Content core-transaction\db\seed\seed-test-data.sql -Raw `
--       | docker exec -i transaction_container psql -U postgres -d transaction_db
--
--   cmd.exe (Windows):
--     docker exec -i transaction_container psql -U postgres -d transaction_db ^
--         < core-transaction\db\seed\seed-test-data.sql
--
--   bash (Linux/macOS/Git Bash):
--     docker exec -i transaction_container psql -U postgres -d transaction_db \
--         < core-transaction/db/seed/seed-test-data.sql
--
--   Альтернатива (универсально) — скопировать файл в контейнер и выполнить -f:
--     docker cp core-transaction/db/seed/seed-test-data.sql transaction_container:/tmp/seed.sql
--     docker exec -i transaction_container psql -U postgres -d transaction_db -f /tmp/seed.sql
--
-- Предполагается, что Liquibase-миграции уже применены и таблицы пустые.
-- При необходимости очистить таблицы — раскомментировать блок TRUNCATE ниже.

BEGIN;

-- 0. Опциональная очистка (раскомментировать для повторной наливки)
-- TRUNCATE cards, accounts, users RESTART IDENTITY CASCADE;
-- ALTER SEQUENCE seq_account_checking  RESTART WITH 1;
-- ALTER SEQUENCE seq_account_savings   RESTART WITH 1;
-- ALTER SEQUENCE seq_account_deposit   RESTART WITH 1;
-- ALTER SEQUENCE seq_account_brokerage RESTART WITH 1;

-- Временная последовательность для уникальных номеров карт
CREATE SEQUENCE IF NOT EXISTS seed_card_seq START 1;

-- 1. USERS (100 000)
INSERT INTO users (first_name, last_name, patronymic, phone_number, email)
SELECT
    (ARRAY['Иван','Пётр','Алексей','Сергей','Дмитрий','Андрей','Мария','Анна','Ольга','Елена'])
        [1 + floor(random()*10)::int],
    (ARRAY['Иванов','Петров','Сидоров','Кузнецов','Смирнов','Попов','Васильев','Соколов','Михайлов','Новиков'])
        [1 + floor(random()*10)::int],
    CASE WHEN random() < 0.7
         THEN (ARRAY['Иванович','Петрович','Сергеевич','Алексеевна','Сергеевна','Андреевна'])
              [1 + floor(random()*6)::int]
         ELSE NULL END,
    '+7' || lpad(i::text, 10, '0'),
    'user' || i || '@example.com'
FROM generate_series(1, 100000) AS i;

-- 2. CHECKING accounts (1-10 на каждого пользователя, обязательно)
INSERT INTO accounts (user_id, account_number, account_type, currency, balance, is_active)
SELECT
    u.id,
    '1' || lpad(nextval('seq_account_checking')::text, 19, '0'),
    'CHECKING',
    'RUB',
    round((random() * 1000000)::numeric, 4),
    TRUE
FROM users u
CROSS JOIN LATERAL generate_series(1, 1 + floor(random()*10)::int) AS n;

-- 3. SAVINGS (~30% пользователей, по одному счёту)
INSERT INTO accounts (user_id, account_number, account_type, currency, balance, is_active)
SELECT
    u.id,
    '2' || lpad(nextval('seq_account_savings')::text, 19, '0'),
    'SAVINGS',
    'RUB',
    round((random() * 1000000)::numeric, 4),
    TRUE
FROM users u
WHERE random() < 0.30;

-- 4. DEPOSIT (~30% пользователей, по одному счёту)
INSERT INTO accounts (user_id, account_number, account_type, currency, balance, is_active)
SELECT
    u.id,
    '3' || lpad(nextval('seq_account_deposit')::text, 19, '0'),
    'DEPOSIT',
    'RUB',
    round((random() * 1000000)::numeric, 4),
    TRUE
FROM users u
WHERE random() < 0.30;

-- 5. BROKERAGE (~30% пользователей, по одному счёту)
INSERT INTO accounts (user_id, account_number, account_type, currency, balance, is_active)
SELECT
    u.id,
    '4' || lpad(nextval('seq_account_brokerage')::text, 19, '0'),
    'BROKERAGE',
    'RUB',
    round((random() * 1000000)::numeric, 4),
    TRUE
FROM users u
WHERE random() < 0.30;

-- 6. CARDS — по одной карте на каждый CHECKING-счёт
INSERT INTO cards (account_id, card_number, expiry_date, is_active)
SELECT
    a.id,
    '4200' || lpad(nextval('seed_card_seq')::text, 12, '0'),
    CURRENT_DATE + INTERVAL '3 years',
    TRUE
FROM accounts a
WHERE a.account_type = 'CHECKING';

-- 7. Уборка
DROP SEQUENCE seed_card_seq;

COMMIT;

-- Сводка по объёмам
SELECT
    (SELECT count(*) FROM users)    AS users,
    (SELECT count(*) FROM accounts) AS accounts,
    (SELECT count(*) FROM cards)    AS cards;

-- Распределение по типам счетов (для быстрой визуальной проверки)
SELECT account_type, count(*) AS cnt
FROM accounts
GROUP BY account_type
ORDER BY account_type;
