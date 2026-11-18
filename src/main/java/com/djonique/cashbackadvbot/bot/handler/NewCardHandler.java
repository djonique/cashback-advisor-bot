package com.djonique.cashbackadvbot.bot.handler;

import com.djonique.cashbackadvbot.bot.BotMessages;
import com.djonique.cashbackadvbot.bot.BotSender;
import com.djonique.cashbackadvbot.bot.CallbackData;
import com.djonique.cashbackadvbot.bot.keyboard.KeyboardFactory;
import com.djonique.cashbackadvbot.config.ReferralProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/*
 * Оформление новой карты: меню банков и реферальные ссылки.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewCardHandler {

    private static final String NEW_CARD_MENU_TEXT =
            "Выберите банк, в котором Вы хотите оформить новую карту:";
    private static final String NEW_CARD_LINK_TEMPLATE =
            "Для оформления карты %s перейдите по ссылке: %s";
    private static final String UNKNOWN_BANK_TEXT = "Неизвестный банк";

    private final BotSender botSender;
    private final KeyboardFactory keyboardFactory;
    private final CallbackSupport callbackSupport;
    private final ReferralProperties referralProperties;

    public void showMenu(CallbackQuery callbackQuery) {
        if (!callbackSupport.editOrSend(callbackQuery, NEW_CARD_MENU_TEXT,
                keyboardFactory.getNewCardMenuKeyboard())) {
            botSender.answerCallback(callbackQuery.getId(), BotMessages.MENU_UPDATE_FAILED);
            return;
        }
        botSender.answerCallback(callbackQuery.getId(), null);
    }

    public void handleBankSelected(CallbackQuery callbackQuery, String data) {
        String bank = data.substring(CallbackData.NEW_CARD_PREFIX.length());
        BankOffer offer = resolveBankOffer(bank);
        if (offer == null) {
            botSender.answerCallback(callbackQuery.getId(), UNKNOWN_BANK_TEXT);
            return;
        }

        String text = String.format(NEW_CARD_LINK_TEMPLATE, offer.name(), offer.url());
        if (!callbackSupport.editOrSend(callbackQuery, text,
                keyboardFactory.getBackToNewCardMenuKeyboard())) {
            botSender.answerCallback(callbackQuery.getId(), BotMessages.MENU_UPDATE_FAILED);
            return;
        }
        botSender.answerCallback(callbackQuery.getId(), null);
    }

    private BankOffer resolveBankOffer(String bank) {
        return switch (bank) {
            case "tbank" -> new BankOffer("Т-Банк", referralProperties.tbank());
            case "alfa" -> new BankOffer("Альфа-Банк", referralProperties.alfa());
            case "vtb" -> new BankOffer("ВТБ", referralProperties.vtb());
            case "ozon" -> new BankOffer("Ozon Банк", referralProperties.ozon());
            case "yandex" -> new BankOffer("Яндекс Банк", referralProperties.yandex());
            case "wb" -> new BankOffer("WB Банк", referralProperties.wb());
            default -> null;
        };
    }

    private record BankOffer(String name, String url) {
    }
}
