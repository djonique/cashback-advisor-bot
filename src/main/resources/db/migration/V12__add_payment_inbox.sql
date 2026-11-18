-- ============================================================================
-- V12__add_payment_inbox.sql
-- Inbox успешных платежей Telegram.
-- Факт оплаты фиксируется до бизнес-обработки, поэтому сбой БД или приложения
-- между списанием денег и записью в payment больше не теряет донат.
-- ============================================================================

CREATE TABLE payment_inbox (
    id                  BIGSERIAL    PRIMARY KEY,
    telegram_charge_id  VARCHAR(255) NOT NULL,
    telegram_chat_id    BIGINT       NOT NULL,
    username            VARCHAR(255),
    invoice_payload     VARCHAR(64),
    provider_charge_id  VARCHAR(255),
    amount              BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    received_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at        TIMESTAMPTZ,
    CONSTRAINT ux_payment_inbox_charge UNIQUE (telegram_charge_id)
);

-- Частичный индекс: fallback-джоба выбирает только необработанные записи.
CREATE INDEX idx_payment_inbox_unprocessed
    ON payment_inbox (received_at)
    WHERE processed_at IS NULL;

COMMENT ON TABLE  payment_inbox                    IS 'Inbox успешных платежей: фиксируется до обработки';
COMMENT ON COLUMN payment_inbox.telegram_charge_id IS 'ID платежа Telegram — ключ идемпотентности';
COMMENT ON COLUMN payment_inbox.telegram_chat_id   IS 'Чат плательщика; FK на app_user нет, пользователь может быть ещё не создан';
COMMENT ON COLUMN payment_inbox.amount             IS 'Сумма в минимальных единицах валюты (копейки/звёзды)';
COMMENT ON COLUMN payment_inbox.processed_at       IS 'NULL — платёж ещё не разобран; заполняется после успешной обработки';
