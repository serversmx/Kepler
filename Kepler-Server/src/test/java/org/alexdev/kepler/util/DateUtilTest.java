package org.alexdev.kepler.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * getReadableSeconds formats the rare cycle countdown shown in the catalogue:
 * Spanish without accents (the v14 catalogue bitmap fonts cannot draw them),
 * zero units skipped, seconds dropped, "y" before the last part.
 */
class DateUtilTest {

    @Test
    void zeroAndSecondsBelowAMinuteReadAsZeroMinutes() {
        assertThat(DateUtil.getReadableSeconds(0)).isEqualTo("0 minutos");
        assertThat(DateUtil.getReadableSeconds(59)).isEqualTo("0 minutos");
        assertThat(DateUtil.getReadableSeconds(-10)).isEqualTo("0 minutos");
    }

    @Test
    void singularAndPluralUnitsJoinedWithY() {
        assertThat(DateUtil.getReadableSeconds(86400 + 3600 + 60 + 1)).isEqualTo("1 dia, 1 hora y 1 minuto");
        assertThat(DateUtil.getReadableSeconds(2 * 86400 + 13 * 3600 + 8 * 60 + 30)).isEqualTo("2 dias, 13 horas y 8 minutos");
    }

    @Test
    void wholeUnitsSkipTheZeroFields() {
        assertThat(DateUtil.getReadableSeconds(2 * 86400)).isEqualTo("2 dias");
        assertThat(DateUtil.getReadableSeconds(3 * 3600)).isEqualTo("3 horas");
    }

    @Test
    void hoursDoNotOverflowIntoDaysBelowAFullDay() {
        assertThat(DateUtil.getReadableSeconds(86399)).isEqualTo("23 horas y 59 minutos");
    }
}
