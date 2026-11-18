package com.djonique.cashbackadvbot.bot.handler;

import com.djonique.cashbackadvbot.bot.BotMessages;
import com.djonique.cashbackadvbot.bot.BotSender;
import com.djonique.cashbackadvbot.bot.keyboard.KeyboardFactory;
import com.djonique.cashbackadvbot.dto.ActiveSelectionDto;
import com.djonique.cashbackadvbot.dto.UserRef;
import com.djonique.cashbackadvbot.service.SelectionService;
import com.djonique.cashbackadvbot.util.TextLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/*
 * Показ текущих настроек кешбэка пользователя.
 * Текст собирается здесь же, чтобы хендлер не тянул сервисы в роутер.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MySelectionsHandler {

    private static final String MY_SELECTIONS_TITLE =
            "📊 Ваши категории кэшбэка на текущий месяц:";
    private static final String NO_SELECTIONS_TEXT =
            "У вас пока нет настроенных категорий на этот месяц.\n\n"
                    + "Нажмите «⚙️ Настроить кэшбэк», чтобы выбрать категории.";
    private static final String ALL_PURCHASES_SLUG = "all_purchases";

    private final BotSender botSender;
    private final KeyboardFactory keyboardFactory;
    private final CallbackSupport callbackSupport;
    private final SelectionService selectionService;

    public void show(CallbackQuery callbackQuery) {
        UserRef user = callbackSupport.resolveUser(callbackQuery);
        if (user == null) {
            botSender.answerCallback(callbackQuery.getId(), BotMessages.USER_NOT_FOUND);
            return;
        }

        List<ActiveSelectionDto> selections = selectionService.getActiveSelections(user.id());
        if (!callbackSupport.editOrSend(callbackQuery, buildMySelectionsText(selections),
                keyboardFactory.getBackToMainKeyboard())) {
            botSender.answerCallback(callbackQuery.getId(), BotMessages.MENU_UPDATE_FAILED);
            return;
        }
        botSender.answerCallback(callbackQuery.getId(), null);
    }

    /*
     * Группирует настройки по банку. Внутри банка «Все покупки» всегда первая,
     * остальные категории — по алфавиту локальных названий (Collator для русского).
     * Порядок банков тоже алфавитный, чтобы сообщение было стабильным.
     */
    static String buildMySelectionsText(List<ActiveSelectionDto> selections) {
        if (selections == null || selections.isEmpty()) {
            return NO_SELECTIONS_TEXT;
        }

        Collator collator = Collator.getInstance(Locale.of("ru", "RU"));
        Comparator<ActiveSelectionDto> categoryOrder = Comparator
                .comparing((ActiveSelectionDto selection) -> !isAllPurchases(selection))
                .thenComparing(ActiveSelectionDto::categoryName, collator);

        Map<String, List<ActiveSelectionDto>> byBank = selections.stream()
                .collect(Collectors.groupingBy(ActiveSelectionDto::bankName));

        StringBuilder text = new StringBuilder(MY_SELECTIONS_TITLE);
        byBank.keySet().stream()
                .sorted(collator)
                .forEach(bankName -> {
                    text.append("\n\n🏦 ").append(bankName).append(':');
                    byBank.get(bankName).stream()
                            .sorted(categoryOrder)
                            .forEach(selection -> text.append("\n• ")
                                    .append(selection.categoryName())
                                    .append(" — ")
                                    .append(formatPercent(selection.percent()))
                                    .append('%'));
                });
        // Telegram отклоняет сообщения длиннее 4096 символов.
        return TextLimiter.truncate(text.toString());
    }

    private static boolean isAllPurchases(ActiveSelectionDto selection) {
        return ALL_PURCHASES_SLUG.equals(selection.categorySlug());
    }

    private static String formatPercent(BigDecimal percent) {
        return percent == null ? "?" : percent.stripTrailingZeros().toPlainString();
    }
}
