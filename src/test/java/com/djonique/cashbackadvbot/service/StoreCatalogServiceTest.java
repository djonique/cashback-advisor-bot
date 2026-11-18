package com.djonique.cashbackadvbot.service;

import com.djonique.cashbackadvbot.domain.Store;
import com.djonique.cashbackadvbot.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreCatalogServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private StoreCatalogService service;

    @Test
    void shouldFindByNameAndAliasIgnoringCaseAndSpaces() {
        Store store = store("Пятёрочка", "пятерочка, pyaterochka");
        when(storeRepository.findAllWithCategory()).thenReturn(List.of(store));

        service.loadOnStartup();

        assertThat(service.findByNameOrAlias("  ПЯТЁРОЧКА ")).containsExactly(store);
        assertThat(service.findByNameOrAlias("pYaterochka")).containsExactly(store);
        assertThat(service.findByNameOrAlias("магнит")).isEmpty();
    }

    @Test
    void shouldMatchOnlyFullNameOrAlias_notPartOfName() {
        Store store = store("Самокат", "самокат, samokat");
        when(storeRepository.findAllWithCategory()).thenReturn(List.of(store));

        service.loadOnStartup();

        assertThat(service.findByNameOrAlias("сам")).isEmpty();
        assertThat(service.findByNameOrAlias("самокат")).containsExactly(store);
    }

    @Test
    void shouldReturnAllStoresSharingAlias() {
        Store first = store("Пятёрочка", "пятерочка");
        Store second = store("Магнит", "пятерочка");
        when(storeRepository.findAllWithCategory()).thenReturn(List.of(first, second));

        service.loadOnStartup();

        assertThat(service.findByNameOrAlias("пятерочка")).containsExactly(first, second);
    }

    @Test
    void shouldLoadStoreWithoutAliases() {
        Store store = store("Самокат", null);
        when(storeRepository.findAllWithCategory()).thenReturn(List.of(store));

        service.loadOnStartup();

        assertThat(service.findByNameOrAlias("самокат")).containsExactly(store);
        assertThat(service.findByNameOrAlias("")).isEmpty();
    }

    @Test
    void shouldPickUpNewStores_whenCacheRefreshed() {
        Store first = store("Пятёрочка", null);
        Store added = store("Магнит", "магнит, magnit");
        when(storeRepository.findAllWithCategory())
                .thenReturn(List.of(first))
                .thenReturn(List.of(first, added));

        service.loadOnStartup();
        assertThat(service.findByNameOrAlias("магнит")).isEmpty();

        service.refresh();

        assertThat(service.findByNameOrAlias("магнит")).containsExactly(added);
        // Старые данные тоже остались доступны.
        assertThat(service.findByNameOrAlias("пятёрочка")).containsExactly(first);
    }

    @Test
    void shouldKeepPreviousSnapshot_whenRefreshFails() {
        Store store = store("Пятёрочка", null);
        when(storeRepository.findAllWithCategory())
                .thenReturn(List.of(store))
                .thenThrow(new RuntimeException("db is down"));

        service.loadOnStartup();
        service.refresh();

        assertThat(service.findByNameOrAlias("пятёрочка")).containsExactly(store);
    }

    @Test
    void shouldLoadCatalogOnStartup() {
        when(storeRepository.findAllWithCategory()).thenReturn(List.of());

        service.loadOnStartup();

        verify(storeRepository).findAllWithCategory();
        assertThat(service.findByNameOrAlias("любой")).isEmpty();
    }

    private Store store(String name, String aliases) {
        Store store = new Store();
        store.setName(name);
        store.setAliases(aliases);
        store.setMccCode("5411");
        return store;
    }
}
