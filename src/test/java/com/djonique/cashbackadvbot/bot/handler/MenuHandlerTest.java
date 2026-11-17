package com.djonique.cashbackadvbot.bot.handler;

import com.djonique.cashbackadvbot.dto.ActiveSelectionDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MenuHandlerTest {

    @Test
    void shouldReportNoSelections_whenNothingConfigured() {
        assertThat(MenuHandler.buildMySelectionsText(List.of()))
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

        assertThat(MenuHandler.buildMySelectionsText(selections))
                .isEqualTo("📊 Ваши категории кэшбэка на текущий месяц:\n\n"
                        + "🏦 Сбербанк:\n"
                        + "• Все покупки — 1%\n"
                        + "• Аптеки — 5%\n"
                        + "• Продукты — 3%\n\n"
                        + "🏦 Т-Банк:\n"
                        + "• Все покупки — 1%\n"
                        + "• Рестораны — 7%");
    }

    private ActiveSelectionDto selection(String bankName, String slug, String categoryName,
                                         String percent) {
        return new ActiveSelectionDto(1L, bankName, 10L, slug, categoryName, new BigDecimal(percent));
    }
}
