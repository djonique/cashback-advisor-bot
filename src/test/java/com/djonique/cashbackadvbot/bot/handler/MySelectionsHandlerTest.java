package com.djonique.cashbackadvbot.bot.handler;

import com.djonique.cashbackadvbot.dto.ActiveSelectionDto;
import com.djonique.cashbackadvbot.util.TextLimiter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MySelectionsHandlerTest {

    @Test
    void shouldReportNoSelections_whenNothingConfigured() {
        assertThat(MySelectionsHandler.buildMySelectionsText(List.of()))
                .isEqualTo("У вас пока нет настроенных категорий на этот месяц.\n\n"
                        + "Нажмите «⚙️ Настроить кэшбэк», чтобы выбрать категории.");
    }

    @Test
    void shouldGroupByBank_withAllPurchasesFirstAndOthersAlphabetically() {
        List<ActiveSelectionDto> selections = List.of(
                selection("Сбербанк", "supermarkets", "Продукты", "3.00"),
                selection("Сбербанк", "all_purchases", "Все покупки", "1.00"),
                selection("Т-Банк", "restaurants", "Рестораны", "7"),
                selection("Сбербанк", "pharmacies", "Аптеки", "5"),
                selection("Т-Банк", "all_purchases", "Все покупки", "1.00"));

        assertThat(MySelectionsHandler.buildMySelectionsText(selections))
                .isEqualTo("📊 Ваши категории кэшбэка на текущий месяц:\n\n"
                        + "🏦 Сбербанк:\n"
                        + "• Все покупки — 1%\n"
                        + "• Аптеки — 5%\n"
                        + "• Продукты — 3%\n\n"
                        + "🏦 Т-Банк:\n"
                        + "• Все покупки — 1%\n"
                        + "• Рестораны — 7%");
    }

    @Test
    void shouldTruncateLongSelectionsText_toTelegramLimit() {
        List<ActiveSelectionDto> selections = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            selections.add(new ActiveSelectionDto(1L, "Банк " + i, 10L,
                    "slug" + i, "Очень длинное название категории " + i, new BigDecimal("5")));
        }

        String text = MySelectionsHandler.buildMySelectionsText(selections);

        assertThat(text).hasSize(TextLimiter.TELEGRAM_MAX_LENGTH);
        assertThat(text).endsWith("…");
    }

    private ActiveSelectionDto selection(String bankName, String slug, String categoryName,
                                         String percent) {
        return new ActiveSelectionDto(1L, bankName, 10L, slug, categoryName, new BigDecimal(percent));
    }
}
