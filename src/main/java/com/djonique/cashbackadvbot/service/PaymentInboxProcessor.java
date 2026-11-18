package com.djonique.cashbackadvbot.service;

import com.djonique.cashbackadvbot.bot.PaymentNotifier;
import com.djonique.cashbackadvbot.domain.PaymentInbox;
import com.djonique.cashbackadvbot.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/*
 * Fallback-обработка платежей, которые не удалось разобрать сразу.
 * processPayment идемпотентен, поэтому повторный вызов безопасен.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentInboxProcessor {

    private static final int BATCH_SIZE = 200;

    private final PaymentInboxService paymentInboxService;
    private final PaymentService paymentService;
    private final PaymentNotifier paymentNotifier;

    @Scheduled(fixedDelay = 60_000)
    public void processPending() {
        List<PaymentInbox> pending = paymentInboxService.findUnprocessed(BATCH_SIZE);
        if (pending.isEmpty()) {
            return;
        }

        log.info("Inbox: необработанных платежей: {}", pending.size());
        for (PaymentInbox inbox : pending) {
            processOne(inbox);
        }
    }

    private void processOne(PaymentInbox inbox) {
        boolean registered;
        try {
            registered = paymentService.processPayment(inbox);
        } catch (RuntimeException e) {
            // processed_at не ставим: джоба повторит попытку через минуту.
            log.error("Inbox: не удалось обработать платёж id={}, chargeId={}: {}",
                    inbox.getId(), inbox.getTelegramChargeId(), LogUtils.sanitize(e));
            return;
        }

        try {
            paymentInboxService.markProcessed(inbox.getId());
        } catch (RuntimeException e) {
            // Платёж уже учтён; отметку поставит следующий проход.
            log.error("Inbox: платёж учтён, но отметка не сохранена: id={}, chargeId={}: {}",
                    inbox.getId(), inbox.getTelegramChargeId(), LogUtils.sanitize(e));
        }

        // false — платёж уже был записан раньше (повторная доставка или
        // параллельный обработчик). Благодарность второй раз не отправляем.
        if (registered) {
            paymentNotifier.sendThanks(inbox.getTelegramChatId(),
                    inbox.getCurrency(), inbox.getTelegramChargeId());
        }

        log.info("Inbox: платёж обработан: id={}, chargeId={}, registered={}",
                inbox.getId(), inbox.getTelegramChargeId(), registered);
    }
}
