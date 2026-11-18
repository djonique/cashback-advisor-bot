package com.djonique.cashbackadvbot.repository;

import com.djonique.cashbackadvbot.domain.PaymentInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentInboxRepository extends JpaRepository<PaymentInbox, Long> {

    /*
     * Фиксирует платёж ровно один раз. При повторной доставке update
     * вернёт 0, а обработка продолжится по уже сохранённой записи.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO payment_inbox (
                telegram_charge_id,
                telegram_chat_id,
                username,
                invoice_payload,
                provider_charge_id,
                amount,
                currency
            ) VALUES (
                :chargeId,
                :chatId,
                :username,
                :invoicePayload,
                :providerChargeId,
                :amount,
                :currency
            )
            ON CONFLICT (telegram_charge_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("chargeId") String chargeId,
                       @Param("chatId") Long chatId,
                       @Param("username") String username,
                       @Param("invoicePayload") String invoicePayload,
                       @Param("providerChargeId") String providerChargeId,
                       @Param("amount") long amount,
                       @Param("currency") String currency);

    Optional<PaymentInbox> findByTelegramChargeId(String telegramChargeId);

    /*
     * Платежи, которые не удалось разобрать в момент получения update.
     * Ограничение в 24 часа не даёт джобе бесконечно ретраить «мёртвые» записи.
     */
    @Query(value = """
            SELECT * FROM payment_inbox
            WHERE processed_at IS NULL
              AND received_at > now() - interval '24 hours'
            ORDER BY received_at
            LIMIT :batchSize
            """, nativeQuery = true)
    List<PaymentInbox> findUnprocessed(@Param("batchSize") int batchSize);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE payment_inbox
            SET processed_at = now()
            WHERE id = :id
              AND processed_at IS NULL
            """, nativeQuery = true)
    int markProcessed(@Param("id") Long id);
}
