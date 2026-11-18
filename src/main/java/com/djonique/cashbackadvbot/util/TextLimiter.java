package com.djonique.cashbackadvbot.util;

/*
 * Обрезка текста под лимит Telegram (4096 символов на сообщение).
 * Оставляем запас в 96 символов: Telegram считает длину в UTF-16 code units,
 * а мы не хотим ловить 400 Bad Request на граничных значениях.
 */
public final class TextLimiter {

    public static final int TELEGRAM_MAX_LENGTH = 4000;
    private static final String ELLIPSIS = "…";

    private TextLimiter() {
    }

    public static String truncate(String text) {
        return truncate(text, TELEGRAM_MAX_LENGTH);
    }

    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 0) {
            return "";
        }
        if (maxLength == 1) {
            return ELLIPSIS;
        }

        int end = maxLength - ELLIPSIS.length();
        // Не разрываем суррогатную пару: иначе в сообщении останется «битый» эмодзи.
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + ELLIPSIS;
    }
}
