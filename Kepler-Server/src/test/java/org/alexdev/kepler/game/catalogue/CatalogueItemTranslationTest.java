package org.alexdev.kepler.game.catalogue;

import org.alexdev.kepler.game.item.ItemManager;
import org.alexdev.kepler.game.item.base.ItemDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CatalogueItemTranslationTest {
    @Test
    void translationsFallBackAndSurviveCopy() {
        ItemManager manager = mock(ItemManager.class);
        ItemDefinition definition = mock(ItemDefinition.class);
        when(manager.getDefinition(anyInt())).thenReturn(definition);
        when(definition.getId()).thenReturn(7);

        try (MockedStatic<ItemManager> singleton = mockStatic(ItemManager.class)) {
            singleton.when(ItemManager::getInstance).thenReturn(manager);

            CatalogueItem item = new CatalogueItem(1, "code", "2", 0, 10, false, 7, 0, "Silla", "Una silla", false, null, null);
            item.setTranslations(Map.of("en", new String[]{"Chair", ""}));

            assertThat(item.getName("en")).isEqualTo("Chair");
            assertThat(item.getDescription("en")).isEqualTo("Una silla");
            assertThat(item.getName("pt")).isEqualTo("Silla");

            CatalogueItem copy = item.copy();
            assertThat(copy.getName("en")).isEqualTo("Chair");
            assertThat(copy.getDescription("en")).isEqualTo("Una silla");
        }
    }
}
