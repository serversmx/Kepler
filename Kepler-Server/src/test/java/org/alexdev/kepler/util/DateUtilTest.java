package org.alexdev.kepler.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * getReadableSeconds breaks a second count into days/hours/minutes/seconds and
 * formats it as a fixed English sentence. The breakdown is pure integer math
 * (no clock), so the boundaries between units are what matter.
 */
class DateUtilTest {

    @Test
    void zeroSecondsReadsAsAllZeroUnits() {
        assertThat(DateUtil.getReadableSeconds(0))
                .isEqualTo("0 days, 0 hours, 0 minutes, 0 seconds");
    }

    @Test
    void secondsBelowAMinuteStayInTheSecondsField() {
        assertThat(DateUtil.getReadableSeconds(59))
                .isEqualTo("0 days, 0 hours, 0 minutes, 59 seconds");
    }

    @Test
    void carriesIntoEveryUnit() {
        // 1 day + 1 hour + 1 minute + 1 second.
        long input = 86400 + 3600 + 60 + 1;

        assertThat(DateUtil.getReadableSeconds(input))
                .isEqualTo("1 days, 1 hours, 1 minutes, 1 seconds");
    }

    @Test
    void wholeUnitsLeaveTheLowerFieldsAtZero() {
        assertThat(DateUtil.getReadableSeconds(2 * 86400))
                .isEqualTo("2 days, 0 hours, 0 minutes, 0 seconds");
        assertThat(DateUtil.getReadableSeconds(3600))
                .isEqualTo("0 days, 1 hours, 0 minutes, 0 seconds");
    }

    @Test
    void hoursDoNotOverflowIntoDaysBelowAFullDay() {
        // 23h59m59s is still 0 days.
        long input = 23 * 3600 + 59 * 60 + 59;

        assertThat(DateUtil.getReadableSeconds(input))
                .isEqualTo("0 days, 23 hours, 59 minutes, 59 seconds");
    }
}
