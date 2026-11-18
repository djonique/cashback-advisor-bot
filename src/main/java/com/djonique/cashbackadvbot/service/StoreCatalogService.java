package com.djonique.cashbackadvbot.service;

import com.djonique.cashbackadvbot.domain.Store;
import com.djonique.cashbackadvbot.repository.StoreRepository;
import com.djonique.cashbackadvbot.util.LogUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
 * In-memory каталог магазинов.
 *
 * Справочник маленький (сотни строк) и меняется редко, поэтому держим его
 * в памяти: поиск по названию и алиасам становится O(1) вместо полного
 * сканирования таблицы на каждый запрос пользователя.
 *
 * Кэш неизменяемый: при обновлении строится новый Map и атомарно
 * подменяется ссылка, поэтому читатели никогда не видят «полусобранный» кэш
 * и не требуют блокировок.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreCatalogService {

    // Обновление раз в час: за это время справочник меняется в лучшем случае вручную.
    private static final long REFRESH_RATE_MS = 3_600_000L;
    private static final String ALIAS_SEPARATOR = ",";

    private final StoreRepository storeRepository;

    private volatile Catalog catalog = Catalog.empty();

    /*
     * Первичная загрузка на старте. Ошибку не глотаем: если БД недоступна,
     * приложение и так не поднимется (Flyway), а работать без магазинов нельзя.
     */
    @PostConstruct
    void loadOnStartup() {
        reload();
        log.info("Каталог магазинов загружен при старте: {} записей", catalog.storeCount());
    }

    /*
     * Требование из ревью: обновлять кэш раз в час, если магазины добавили в БД.
     * Ошибку обновления логируем и оставляем прошлый снимок — поиск продолжает
     * работать на старых данных вместо падения.
     */
    @Scheduled(fixedRate = REFRESH_RATE_MS, initialDelay = REFRESH_RATE_MS)
    public void refresh() {
        try {
            reload();
            log.info("Каталог магазинов обновлён: {} записей", catalog.storeCount());
        } catch (RuntimeException e) {
            log.error("Не удалось обновить каталог магазинов, работаем на прошлом снимке: {}",
                    LogUtils.sanitize(e));
        }
    }

    /*
     * Точное совпадение без учёта регистра — по названию или по любому алиасу.
     * Возвращает несколько магазинов, если один алиас встречается у разных сетей.
     */
    public List<Store> findByNameOrAlias(String text) {
        List<Store> stores = catalog.byKey().get(normalize(text));
        return stores == null ? List.of() : stores;
    }

    private void reload() {
        List<Store> stores = storeRepository.findAllWithCategory();
        catalog = new Catalog(buildIndex(stores), stores.size());
    }

    /*
     * Индекс: ключ — нормализованное название или алиас, значение — магазины
     * с таким ключом. Category у Store загружена через EntityGraph, поэтому
     * обращение к ней после закрытия сессии безопасно.
     */
    private Map<String, List<Store>> buildIndex(List<Store> stores) {
        Map<String, LinkedHashSet<Store>> index = new HashMap<>();
        for (Store store : stores) {
            addKey(index, store.getName(), store);
            if (store.getAliases() == null || store.getAliases().isBlank()) {
                continue;
            }
            // Алиасы хранятся одной строкой через запятую.
            for (String alias : store.getAliases().split(ALIAS_SEPARATOR)) {
                addKey(index, alias, store);
            }
        }

        Map<String, List<Store>> immutable = new HashMap<>(index.size());
        // LinkedHashSet убирает дубли, когда алиас совпадает с названием магазина.
        index.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return Map.copyOf(immutable);
    }

    private void addKey(Map<String, LinkedHashSet<Store>> index, String rawKey, Store store) {
        String key = normalize(rawKey);
        if (key.isEmpty()) {
            return;
        }
        index.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(store);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Catalog(Map<String, List<Store>> byKey, int storeCount) {

        static Catalog empty() {
            return new Catalog(Map.of(), 0);
        }
    }
}
