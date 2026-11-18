package com.djonique.cashbackadvbot.bot;

import com.djonique.cashbackadvbot.util.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/*
 * Благодарность за платёж. Используется и живым обработчиком update,
 * и fallback-джобой, чтобы текст сообщения был в одном месте.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentNotifier {

    private static final String RUB_CURRENCY = "RUB";
    private static final String STARS_CURRENCY = "XTR";
    private static final String THANKS_RUB_TEMPLATE =
            "Спасибо за поддержку! ❤️ (ID транзакции: %s)";
    private static final String THANKS_STARS_TEXT = "Спасибо за поддержку! ❤️";

    private final BotSender botSender;

    public void sendThanks(Long chatId, String currency, String telegramChargeId) {
        if (RUB_CURRENCY.equals(currency)) {
            botSender.send(chatId, String.format(THANKS_RUB_TEMPLATE, telegramChargeId), null);
        } else if (STARS_CURRENCY.equals(currency)) {
            botSender.send(chatId, THANKS_STARS_TEXT, null);
        } else {
            log.warn("Неизвестная валюта успешного платежа: chatId={}, currency={}",
                    LogUtils.maskChatId(chatId), currency);
        }
    }
}
