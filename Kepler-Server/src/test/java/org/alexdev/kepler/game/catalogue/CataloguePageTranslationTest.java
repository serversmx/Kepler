package org.alexdev.kepler.game.catalogue;

import org.alexdev.kepler.game.player.PlayerDetails;
import org.alexdev.kepler.game.player.PlayerRank;
import org.alexdev.kepler.util.config.GameConfiguration;
import org.alexdev.kepler.util.config.writer.GameConfigWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CataloguePageTranslationTest {
    @BeforeAll
    static void config() {
        GameConfiguration.reset(new GameConfigWriter());
    }

    private CataloguePage page() {
        return new CataloguePage(5, PlayerRank.NORMAL, true, false, "frontpage", "", "Portada", "ctlg_layout1", "", "",
                "Cuerpo es", "Elegir", "Extra es", "1:Uno\r\n2:Dos");
    }

    @Test
    void untranslatedLocaleUsesSpanish() {
        CataloguePage.Text t = page().getText("en");
        assertThat(t.getName()).isEqualTo("Portada");
        assertThat(t.getBody()).isEqualTo("Cuerpo es");
        assertThat(t.getLabelExtra()).containsEntry("label_extra_t_1", "Uno");
    }

    @Test
    void fallbackIsPerColumn() {
        CataloguePage page = page();
        CataloguePage.Text en = page.translate("Home", "", null, "Extra en", "");
        page.setTranslations(Map.of("en", en));

        CataloguePage.Text t = page.getText("en");
        assertThat(t.getName()).isEqualTo("Home");
        assertThat(t.getBody()).isEqualTo("Cuerpo es");
        assertThat(t.getLabelPick()).isEqualTo("Elegir");
        assertThat(t.getLabelExtra_s()).isEqualTo("Extra en");
        assertThat(t.getLabelExtra()).containsEntry("label_extra_t_2", "Dos");
        assertThat(page.getText("pt").getName()).isEqualTo("Portada");
        assertThat(page.getName()).isEqualTo("Portada");
    }

    @Test
    void translatedLabelExtraIsParsedLikeTheOriginal() {
        CataloguePage page = page();
        page.setTranslations(Map.of("pt", page.translate(null, null, null, null, "1:Um\r\n3:Tres")));

        assertThat(page.getText("pt").getLabelExtra())
                .containsEntry("label_extra_t_1", "Um").containsEntry("label_extra_t_3", "Tres")
                .doesNotContainKey("label_extra_t_2");
    }

    @Test
    void playerDetailsLocaleIsNormalised() {
        PlayerDetails details = new PlayerDetails();
        assertThat(details.getLocale()).isEqualTo("es");
        details.setLocale("PT-br");
        assertThat(details.getLocale()).isEqualTo("pt");
        details.setLocale("xx");
        assertThat(details.getLocale()).isEqualTo("es");
    }
}
