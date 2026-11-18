-- ============================================================================
-- V13__cleanup_and_performance.sql
-- Чистка мёртвых объектов и подготовка payment_invoice к статусу EXPIRED.
-- Только новые объекты/изменения, данные не удаляются.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Мёртвые trigram-индексы.
-- Точное сравнение магазинов и категорий выполняется в памяти (StoreCatalogService)
-- и через UNIQUE-индексы, поэтому GIN-индексы только замедляют запись.
-- ----------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_category_name_trgm;
DROP INDEX IF EXISTS idx_category_alias_alias_trgm;
DROP INDEX IF EXISTS idx_store_name_trgm;
DROP INDEX IF EXISTS idx_store_aliases_trgm;

-- Расширение pg_trgm намеренно не удаляем: DROP EXTENSION затрагивает все схемы
-- и требует отдельной проверки зависимостей. После удаления индексов оно
-- ничего не ускоряет, но и не мешает.

-- ----------------------------------------------------------------------------
-- 2. Статус EXPIRED для брошенных счетов.
-- Счёт живёт 24 часа: если за это время оплата не пришла, джоба помечает его
-- EXPIRED, и PreCheckoutQuery по нему больше не подтверждается.
-- ----------------------------------------------------------------------------
ALTER TABLE payment_invoice
    DROP CONSTRAINT IF EXISTS chk_payment_invoice_status;

ALTER TABLE payment_invoice
    ADD CONSTRAINT chk_payment_invoice_status
        CHECK (status IN ('CREATED', 'PAID', 'EXPIRED'));

COMMENT ON COLUMN payment_invoice.status IS 'CREATED | PAID | EXPIRED';

-- Под ежедневную джобу: выборка «CREATED, созданные раньше порога».
-- Композитный индекс покрывает и фильтрацию только по status (левый префикс),
-- поэтому старый idx_payment_invoice_status становится избыточным.
CREATE INDEX IF NOT EXISTS idx_payment_invoice_created_at
    ON payment_invoice (status, created_at);

DROP INDEX IF EXISTS idx_payment_invoice_status;

-- ----------------------------------------------------------------------------
-- 3. app_user.timezone нигде не используется: всё расписание захардкожено
-- на Europe/Moscow. Индекс по колонке удаляем вместе с самой колонкой.
-- ----------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_app_user_timezone;

ALTER TABLE app_user
    DROP COLUMN IF EXISTS timezone;
