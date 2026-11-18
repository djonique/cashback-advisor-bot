package com.djonique.cashbackadvbot.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextLimiterTest {

    @Test
    void shouldReturnNull_andShortTextUnchanged() {
        assertThat(TextLimiter.truncate(null)).isNull();
        assertThat(TextLimiter.truncate("короткий текст")).isEqualTo("короткий текст");
    }

    @Test
    void shouldNotChangeTextOfExactMaxLength() {
        String text = "a".repeat(TextLimiter.TELEGRAM_MAX_LENGTH);

        assertThat(TextLimiter.truncate(text)).isEqualTo(text);
    }

    @Test
    void shouldCutTextToLimitAndAddEllipsis() {
        String text = "a".repeat(TextLimiter.TELEGRAM_MAX_LENGTH + 500);

        String result = TextLimiter.truncate(text);

        assertThat(result).hasSize(TextLimiter.TELEGRAM_MAX_LENGTH);
        assertThat(result).endsWith("…");
    }

    @Test
    void shouldNotSplitSurrogatePair_whenCutFallsInsideEmoji() {
        // Эмодзи занимает две UTF-16 единицы: обрезка не должна оставить половину.
        // 3998 'a' + эмодзи: граница обрезки попадает ровно между его половинами.
        String text = "a".repeat(3998) + "😀" + "b".repeat(100);

        String result = TextLimiter.truncate(text, 4000);

        assertThat(result.length()).isLessThanOrEqualTo(4000);
        assertThat(result).endsWith("…");
        assertThat(Character.isHighSurrogate(result.charAt(result.length() - 2))).isFalse();
    }

    @Test
    void shouldHandleEdgeMaxLengths() {
        assertThat(TextLimiter.truncate("abcdef", 0)).isEmpty();
        assertThat(TextLimiter.truncate("abcdef", 1)).isEqualTo("…");
        assertThat(TextLimiter.truncate("abcdef", 2)).isEqualTo("a…");
    }
}
