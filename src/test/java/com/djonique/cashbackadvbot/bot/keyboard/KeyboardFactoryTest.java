package com.djonique.cashbackadvbot.bot.keyboard;

import com.djonique.cashbackadvbot.bot.BotButtons;
import com.djonique.cashbackadvbot.bot.CallbackData;
import com.djonique.cashbackadvbot.config.BotProperties;
import com.djonique.cashbackadvbot.dto.BankCategoryDto;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KeyboardFactoryTest {

    private final BotProperties botProperties = new BotProperties();
    private final KeyboardFactory keyboardFactory = new KeyboardFactory(botProperties);

    @Test
    void shouldPutAllPurchasesFirstAndSortOthersAlphabetically() {
        List<BankCategoryDto> categories = List.of(
                new BankCategoryDto(1L, "supermarkets", "Супермаркеты"),
                new BankCategoryDto(2L, "all_purchases", "Все покупки"),
                new BankCategoryDto(3L, "taxi", "Такси"),
                new BankCategoryDto(4L, "pharmacies", "Аптеки"));

        InlineKeyboardMarkup markup = keyboardFactory.getCategoryKeyboard(categories, Set.of());

        assertThat(markup.getKeyboard())
                .extracting(row -> row.getFirst().getText())
                .startsWith("⬜ Все покупки", "⬜ Аптеки", "⬜ Супермаркеты", "⬜ Такси");
    }

    @Test
    void shouldAddMySelectionsButtonWithCurrentRussianMonth() {
        InlineKeyboardMarkup markup = keyboardFactory.getMainMenuKeyboard();

        assertThat(markup.getKeyboard().stream()
                .flatMap(List::stream)
                .filter(button -> CallbackData.MENU_MY_SELECTIONS.equals(button.getCallbackData())))
                .singleElement()
                .extracting(InlineKeyboardButton::getText)
                .isEqualTo("📋 Мои настройки на " + currentMonthName());
    }

    @Test
    void shouldBuildMainMenuWithoutShareButtonInFixedOrder() {
        List<List<InlineKeyboardButton>> rows = keyboardFactory.getMainMenuKeyboard().getKeyboard();

        assertThat(rows)
                .extracting(row -> row.getFirst().getText())
                .containsExactly(
                        "💳 " + BotButtons.CHOOSE_CARD,
                        "📋 Мои настройки на " + currentMonthName(),
                        "⚙️ " + BotButtons.SETUP,
                        "💎 " + BotButtons.NEW_CARD,
                        "❤️ " + BotButtons.SUPPORT,
                        "🆘 " + BotButtons.HELP);

        assertThat(rows)
                .extracting(row -> row.getFirst().getCallbackData())
                .containsExactly(
                        CallbackData.MENU_CHOOSE,
                        CallbackData.MENU_MY_SELECTIONS,
                        CallbackData.MENU_SETUP,
                        CallbackData.MENU_NEW_CARD,
                        CallbackData.MENU_SUPPORT,
                        CallbackData.MENU_HELP);
    }

    @Test
    void shouldBuildShareKeyboardWithUrlButton() {
        botProperties.setBotUsername("TestCashbackBot");

        InlineKeyboardButton button = keyboardFactory.getShareKeyboard()
                .getKeyboard().getFirst().getFirst();

        assertThat(button.getText()).isEqualTo("Отправить друзьям");
        assertThat(button.getCallbackData()).isNull();
        assertThat(button.getUrl())
                .isEqualTo("https://t.me/share/url?url=https://t.me/TestCashbackBot&text="
                        + URLEncoder.encode("Попробуй этого бота для выбора кешбэка!",
                                StandardCharsets.UTF_8));
    }

    private String currentMonthName() {
        return YearMonth.now(ZoneId.of("Europe/Moscow"))
                .getMonth()
                .getDisplayName(TextStyle.FULL_STANDALONE, Locale.of("ru"))
                .toLowerCase(Locale.of("ru"));
    }
}
