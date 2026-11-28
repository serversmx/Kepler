package org.alexdev.kepler.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocaleUtilTest {
    @Test
    void normalizesSupportedLocales() {
        assertThat(LocaleUtil.normalize("en")).isEqualTo("en");
        assertThat(LocaleUtil.normalize("PT")).isEqualTo("pt");
        assertThat(LocaleUtil.normalize("pt-br")).isEqualTo("pt");
        assertThat(LocaleUtil.normalize("en-US")).isEqualTo("en");
        assertThat(LocaleUtil.normalize(" en ")).isEqualTo("en");
    }

    @Test
    void anythingElseIsSpanish() {
        assertThat(LocaleUtil.normalize(null)).isEqualTo("es");
        assertThat(LocaleUtil.normalize("")).isEqualTo("es");
        assertThat(LocaleUtil.normalize("fr")).isEqualTo("es");
        assertThat(LocaleUtil.normalize("e")).isEqualTo("es");
        assertThat(LocaleUtil.normalize("es")).isEqualTo("es");
    }

    @Test
    void pickFallsBackWhenNullOrBlank() {
        assertThat(LocaleUtil.pick(null, "es")).isEqualTo("es");
        assertThat(LocaleUtil.pick("", "es")).isEqualTo("es");
        assertThat(LocaleUtil.pick("  ", "es")).isEqualTo("es");
        assertThat(LocaleUtil.pick("en", "es")).isEqualTo("en");
    }
}
