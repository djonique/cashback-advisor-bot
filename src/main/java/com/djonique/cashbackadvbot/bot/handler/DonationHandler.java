package com.djonique.cashbackadvbot.bot.handler;

import com.djonique.cashbackadvbot.bot.BotMessages;
import com.djonique.cashbackadvbot.bot.BotSender;
import com.djonique.cashbackadvbot.bot.CallbackData;
import com.djonique.cashbackadvbot.bot.FixedDonation;
import com.djonique.cashbackadvbot.bot.keyboard.KeyboardFactory;
import com.djonique.cashbackadvbot.bot.state.DialogState;
import com.djonique.cashbackadvbot.dto.DonationPromptContext;
import com.djonique.cashbackadvbot.dto.UserRef;
import com.djonique.cashbackadvbot.exception.ValidationException;
import com.djonique.cashbackadvbot.service.PaymentService;
import com.djonique.cashbackadvbot.service.UserStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.Set;

/*
 * Донаты: меню поддержки, звезды Telegram и фиксированные/произвольные суммы.
 * Здесь же живёт служебное сообщение «введите сумму», поэтому очистка
 * этого промпта доступна роутеру через deletePromptIfAny.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DonationHandler {

    private static final String CHAT_ERROR_TEXT = "Не удалось определить чат";
    private static final String DONATION_MENU_TEXT = "Ваша поддержка очень важна. Выберите формат:";
    private static final String STARS_MENU_TEXT = "Выберите количество звезд для поддержки проекта:";
    private static final String STARS_TITLE = "Донат звездами Telegram";
    private static final String INVALID_AMOUNT_TEXT = "Некорректная сумма";
    private static final String INVOICE_ERROR_TEXT = "Не удалось выставить счёт, попробуйте позже";
    private static final Set<Integer> STARS_AMOUNTS = Set.of(1, 5, 10, 25, 50, 100);
    private static final String DONATION_TITLE_TEMPLATE = "Донат %d руб";
    private static final String DONATION_AMOUNT_PROMPT =
            "Введите сумму доната в рублях (целое число, минимум 100 ₽):";
    private static final String SEND_ERROR_TEXT = "Не удалось отправить сообщение";

    private final BotSender botSender;
    private final KeyboardFactory keyboardFactory;
    private final CallbackSupport callbackSupport;
    private final UserStateService userStateService;
    private final PaymentService paymentService;
    private final ObjectProvider<AbsSender> absSenderProvider;

    public void sendMenu(Long chatId) {
        botSender.send(chatId, DONATION_MENU_TEXT, keyboardFactory.getDonationMenuKeyboard());
    }

    public void showMenu(CallbackQuery callbackQuery) {
        if (!callbackSupport.editOrSend(callbackQuery, DONATION_MENU_TEXT,
                keyboardFactory.getDonationMenuKeyboard())) {
            botSender.answerCallback(callbackQuery.getId(), BotMessages.MENU_UPDATE_FAILED);
            return;
        }
        botSender.answerCallback(callbackQuery.getId(), null);
    }

    public void showStarsMenu(CallbackQuery callbackQuery) {
        if (!callbackSupport.editOrSend(callbackQuery, STARS_MENU_TEXT,
                keyboardFactory.getStarsMenuKeyboard())) {
            botSender.answerCallback(callbackQuery.getId(), BotMessages.MENU_UPDATE_FAILED);
            return;
        }
        botSender.answerCallback(callbackQuery.getId(), null);
    }

    public void handleStarsDonation(CallbackQuery callbackQuery, String data) {
        Long chatId = callbackSupport.getChatId(callbackQuery);
        if (chatId == null) {
            botSender.answerCallback(callbackQuery.getId(), CHAT_ERROR_TEXT);
            return;
        }

        Integer amount = parseStarsAmount(data);
        if (amount == null || !STARS_AMOUNTS.contains(amount)) {
            botSender.answerCallback(callbackQuery.getId(), INVALID_AMOUNT_TEXT);
            return;
        }

        if (!paymentService.sendStarsInvoice(chatId, STARS_TITLE, amount,
                absSenderProvider.getObject())) {
            botSender.answerCallback(callbackQuery.getId(), INVOICE_ERROR_TEXT);
            return;
        }
        botSender.answerCallback(callbackQuery.getId(), null);
    }

    public void handleFixedDonation(CallbackQuery callbackQuery, String data) {
        Long chatId = callbackSupport.getChatId(callbackQuery);
        if (chatId == null) {
            botSender.answerCallback(callbackQuery.getId(), CHAT_ERROR_TEXT);
            return;
        }

        FixedDonation donation = FixedDonation.fromCallback(data).orElse(null);
        if (donation == null) {
            botSender.answerCallback(callbackQuery.getId(), INVALID_AMOUNT_TEXT);
            return;
        }

        String title = String.format(DONATION_TITLE_TEMPLATE, donation.getAmountRub());
        if (!paymentService.sendFiatInvoice(chatId, title, donation.getAmountRub(),
                absSenderProvider.getObject())) {
            botSender.answerCallback(callbackQuery.getId(), INVOICE_ERROR_TEXT);
            return;
        }
        botSender.answerCallback(callbackQuery.getId(), null);
    }

    public void handleCustomDonation(CallbackQuery callbackQuery) {
        UserRef user = callbackSupport.resolveUser(callbackQuery);
        Long chatId = callbackSupport.getChatId(callbackQuery);
        if (user == null || chatId == null) {
            botSender.answerCallback(callbackQuery.getId(), CHAT_ERROR_TEXT);
            return;
        }

        deletePromptIfAny(user, chatId);
        Integer promptMessageId = botSender.sendWithMessageId(chatId, DONATION_AMOUNT_PROMPT, null);
        if (promptMessageId == null) {
            botSender.answerCallback(callbackQuery.getId(), SEND_ERROR_TEXT);
            return;
        }

        userStateService.setState(user.id(), DialogState.ENTERING_DONATION_AMOUNT,
                new DonationPromptContext(promptMessageId));
        botSender.answerCallback(callbackQuery.getId(), null);
    }

    /*
     * Удаляет сообщение «введите сумму», если пользователь в этом состоянии.
     * Вызывается и из роутера при любом другом действии, чтобы промпт не висел.
     */
    public void deletePromptIfAny(UserRef user, Long chatId) {
        if (userStateService.getState(user.id()) != DialogState.ENTERING_DONATION_AMOUNT) {
            return;
        }

        try {
            userStateService.getContext(user.id(), DonationPromptContext.class)
                    .map(DonationPromptContext::promptMessageId)
                    .ifPresent(messageId -> botSender.deleteMessage(chatId, messageId));
        } catch (ValidationException e) {
            log.warn("Не удалось прочитать контекст доната: userId={}", user.id());
        }
    }

    private Integer parseStarsAmount(String data) {
        try {
            return Integer.valueOf(data.substring(CallbackData.DONATE_STAR_PREFIX.length()));
        } catch (NumberFormatException e) {
            log.warn("Некорректное количество звезд в callback: '{}'", data);
            return null;
        }
    }
}
