package com.djonique.cashbackadvbot.bot.handler;

import com.djonique.cashbackadvbot.bot.BotMessages;
import com.djonique.cashbackadvbot.bot.BotSender;
import com.djonique.cashbackadvbot.bot.CallbackData;
import com.djonique.cashbackadvbot.dto.UserRef;
import com.djonique.cashbackadvbot.service.UserService;
import com.djonique.cashbackadvbot.service.UserStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/*
 * Единая точка входа для команд и callback-кнопок.
 * Роутер только выбирает хендлер и очищает FSM-состояние;
 * вся бизнес-логика живёт в специализированных классах.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MenuHandler {

    private static final String INVALID_CALLBACK_TEXT = "Некорректный запрос";

    private final UserService userService;
    private final UserStateService userStateService;
    private final BotSender botSender;
    private final CallbackSupport callbackSupport;
    private final NavigationHandler navigationHandler;
    private final DonationHandler donationHandler;
    private final NewCardHandler newCardHandler;
    private final MySelectionsHandler mySelectionsHandler;
    private final CardSearchHandler cardSearchHandler;
    private final SetupHandler setupHandler;

    public void handleCommand(String command, Long chatId, String username) {
        UserRef user = userService.findOrCreate(chatId, username);
        clearStateAndDeletePrompt(user, chatId);
        switch (command) {
            case "start" -> navigationHandler.sendStart(chatId, user);
            case "menu" -> navigationHandler.sendMenu(chatId, user);
            case "settings" -> setupHandler.openBankSelection(user, chatId);
            case "help" -> navigationHandler.sendHelp(chatId);
            case "donate" -> donationHandler.sendMenu(chatId);
            case "share" -> navigationHandler.sendShare(chatId);
            default -> log.warn("Неизвестная команда: {}", command);
        }
    }

    public void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        if (data == null) {
            log.warn("Callback без data: id={}", callbackQuery.getId());
            botSender.answerCallback(callbackQuery.getId(), INVALID_CALLBACK_TEXT);
            return;
        }

        // Состояние настройки сохраняют только шаги самого мастера.
        if (!isSetupCallback(data)) {
            clearStateAndDeletePrompt(callbackQuery);
        }

        switch (data) {
            case CallbackData.MENU_MAIN -> navigationHandler.showMainMenu(callbackQuery);
            case CallbackData.MENU_CHOOSE -> cardSearchHandler.handleChoose(callbackQuery);
            case CallbackData.CARD_MENU -> cardSearchHandler.handleMenu(callbackQuery);
            case CallbackData.CARD_BY_CATEGORY -> cardSearchHandler.handleCardByCategory(callbackQuery);
            case CallbackData.CARD_BY_MCC -> cardSearchHandler.handleCardByMcc(callbackQuery);
            case CallbackData.CARD_BY_STORE -> cardSearchHandler.handleCardByStore(callbackQuery);
            case CallbackData.MENU_SETUP -> setupHandler.handleSetup(callbackQuery);
            case CallbackData.MENU_MY_SELECTIONS -> mySelectionsHandler.show(callbackQuery);
            case CallbackData.SETUP_DONE -> setupHandler.handleDone(callbackQuery);
            case CallbackData.MENU_NEW_CARD -> newCardHandler.showMenu(callbackQuery);
            case CallbackData.MENU_SUPPORT -> donationHandler.showMenu(callbackQuery);
            case CallbackData.DONATE_STARS -> donationHandler.showStarsMenu(callbackQuery);
            case CallbackData.SUPPORT_100,
                 CallbackData.SUPPORT_250,
                 CallbackData.SUPPORT_500 -> donationHandler.handleFixedDonation(callbackQuery, data);
            case CallbackData.SUPPORT_CUSTOM -> donationHandler.handleCustomDonation(callbackQuery);
            case CallbackData.MENU_HELP -> navigationHandler.showHelpMenu(callbackQuery);
            case CallbackData.HELP_GENERAL, CallbackData.HELP_SETUP, CallbackData.HELP_STORE,
                 CallbackData.HELP_CATEGORY, CallbackData.HELP_MCC ->
                    navigationHandler.showHelpSection(callbackQuery, data);
            default -> handlePrefixedCallback(data, callbackQuery);
        }
    }

    private boolean isSetupCallback(String data) {
        return data.equals(CallbackData.SETUP_DONE)
                || data.startsWith(CallbackData.SETUP_BANK_PREFIX)
                || data.startsWith(CallbackData.SETUP_TOGGLE_PREFIX);
    }

    private void handlePrefixedCallback(String data, CallbackQuery callbackQuery) {
        if (data.startsWith(CallbackData.DONATE_STAR_PREFIX)) {
            donationHandler.handleStarsDonation(callbackQuery, data);
        } else if (data.startsWith(CallbackData.NEW_CARD_PREFIX)) {
            newCardHandler.handleBankSelected(callbackQuery, data);
        } else if (data.startsWith(CallbackData.SETUP_BANK_PREFIX)) {
            setupHandler.handleBankSelected(data, callbackQuery);
        } else if (data.startsWith(CallbackData.SETUP_TOGGLE_PREFIX)) {
            setupHandler.handleCategoryToggle(data, callbackQuery);
        } else {
            log.warn("Неизвестный callback: {}", data);
            botSender.answerCallback(callbackQuery.getId(), BotMessages.STALE_BUTTON);
        }
    }

    private void clearStateAndDeletePrompt(CallbackQuery callbackQuery) {
        UserRef user = callbackSupport.resolveUser(callbackQuery);
        Long chatId = callbackSupport.getChatId(callbackQuery);
        if (user == null || chatId == null) {
            return;
        }
        clearStateAndDeletePrompt(user, chatId);
    }

    private void clearStateAndDeletePrompt(UserRef user, Long chatId) {
        donationHandler.deletePromptIfAny(user, chatId);
        userStateService.clearState(user.id());
    }
}
