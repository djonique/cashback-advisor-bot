package com.djonique.cashbackadvbot.service;

import com.djonique.cashbackadvbot.domain.PaymentInbox;
import com.djonique.cashbackadvbot.repository.PaymentInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentInboxService {

    private final PaymentInboxRepository paymentInboxRepository;

    /*
     * Сохраняет платёж в inbox идемпотентно: повторный вызов с тем же
     * telegramChargeId вернёт уже существующую запись.
     */
    @Transactional
    public PaymentInbox save(Long chatId,
                             String username,
                             String chargeId,
                             String providerChargeId,
                             long amount,
                             String currency,
                             String invoicePayload) {
        int inserted = paymentInboxRepository.insertIfAbsent(
                chargeId, chatId, username, invoicePayload, providerChargeId, amount, currency);

        PaymentInbox inbox = paymentInboxRepository.findByTelegramChargeId(chargeId)
                .orElseThrow(() -> new IllegalStateException(
                        "Не удалось прочитать payment_inbox для chargeId=" + chargeId));

        log.info("Платёж зафиксирован в inbox: id={}, chargeId={}, inserted={}",
                inbox.getId(), chargeId, inserted);
        return inbox;
    }

    @Transactional
    public void markProcessed(Long inboxId) {
        int updated = paymentInboxRepository.markProcessed(inboxId);
        if (updated == 0) {
            log.debug("Платёж inbox уже обработан: id={}", inboxId);
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentInbox> findUnprocessed(int batchSize) {
        return paymentInboxRepository.findUnprocessed(batchSize);
    }
}
