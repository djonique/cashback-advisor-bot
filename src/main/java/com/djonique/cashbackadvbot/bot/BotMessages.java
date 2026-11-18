package com.djonique.cashbackadvbot.bot;

/*
 * Общие тексты ответов бота.
 * Держим их в одном месте, чтобы формулировки не расходились по хендлерам.
 */
public final class BotMessages {

    public static final String USER_NOT_FOUND = "Не удалось определить пользователя";
    public static final String MENU_UPDATE_FAILED = "Не удалось обновить меню";
    public static final String STALE_BUTTON = "Кнопка устарела, начните заново";

    private BotMessages() {
    }
}
