package com.djonique.cashbackadvbot.bot.handler;

import com.djonique.cashbackadvbot.bot.BotMessages;
import com.djonique.cashbackadvbot.bot.BotSender;
import com.djonique.cashbackadvbot.bot.keyboard.KeyboardFactory;
import com.djonique.cashbackadvbot.dto.UserRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/*
 * Навигация: главное меню, /start, /menu, /share и переходы в раздел помощи.
 * Не знает ни о платежах, ни о настройках кешбэка.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NavigationHandler {

    private static final String START_TEXT =
            "Привет! Я помогу выбрать карту для оплаты. Выберите действие, либо отправьте название магазина, категорию кешбэка или МСС-код в текстовом сообщении";
    private static final String MENU_TEXT = "Отправьте сообщение с названием магазина, категорию кешбэка или МСС-код";
    private static final String SHARE_TEXT =
            "🤝 Поделитесь ботом с друзьями! Пусть они тоже выбирают карты с умом:";

    private final BotSender botSender;
    private final KeyboardFactory keyboardFactory;
    private final CallbackSupport callbackSupport;
    private final HelpHandler helpHandler;

    public void sendStart(Long chatId, UserRef user) {
        log.info("Обработан /start: userId={}", user.id());
        botSender.send(chatId, START_TEXT, keyboardFactory.getMainMenuKeyboard());
    }

    public void sendMenu(Long chatId, UserRef user) {
        log.info("Обработан /menu: userId={}", user.id());
        botSender.send(chatId, MENU_TEXT, keyboardFactory.getMainMenuKeyboard());
    }

    public void sendShare(Long chatId) {
        botSender.send(chatId, SHARE_TEXT, keyboardFactory.getShareKeyboard());
    }

    public void sendHelp(Long chatId) {
        helpHandler.sendHelp(chatId);
    }

    public void showMainMenu(CallbackQuery callbackQuery) {
        if (!callbackSupport.editOrSend(callbackQuery, MENU_TEXT,
                keyboardFactory.getMainMenuKeyboard())) {
            botSender.answerCallback(callbackQuery.getId(), BotMessages.MENU_UPDATE_FAILED);
            return;
        }
        botSender.answerCallback(callbackQuery.getId(), null);
    }

    public void showHelpMenu(CallbackQuery callbackQuery) {
        helpHandler.handleMenu(callbackQuery);
    }

    public void showHelpSection(CallbackQuery callbackQuery, String data) {
        helpHandler.handleHelp(callbackQuery, data);
    }
}
