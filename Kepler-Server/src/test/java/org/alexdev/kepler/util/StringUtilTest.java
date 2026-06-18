package org.alexdev.kepler.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StringUtilTest {

    @Test
    void isNullOrEmptyTreatsNullBlankAndWhitespaceAsEmpty() {
        assertThat(StringUtil.isNullOrEmpty(null)).isTrue();
        assertThat(StringUtil.isNullOrEmpty("")).isTrue();
        assertThat(StringUtil.isNullOrEmpty("   ")).isTrue();
        assertThat(StringUtil.isNullOrEmpty("x")).isFalse();
        assertThat(StringUtil.isNullOrEmpty(" hello ")).isFalse();
    }

    @Test
    void paginateChunksEvenlyAndKeepsTheRemainder() {
        Map<Integer, List<Integer>> chunks = StringUtil.paginate(List.of(1, 2, 3, 4, 5), 2);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).containsExactly(1, 2);
        assertThat(chunks.get(1)).containsExactly(3, 4);
        assertThat(chunks.get(2)).containsExactly(5);
    }

    @Test
    void paginateWithExactMultipleHasNoTrailingChunk() {
        Map<Integer, List<Integer>> chunks = StringUtil.paginate(List.of(1, 2, 3, 4), 2);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1)).containsExactly(3, 4);
    }

    @Test
    void formatRoundsToTwoDecimalPlaces() {
        assertThat(StringUtil.format(3.14159)).isCloseTo(3.14, within(1e-9));
        assertThat(StringUtil.format(2.567)).isCloseTo(2.57, within(1e-9));
        assertThat(StringUtil.format(1.5)).isCloseTo(1.5, within(1e-9));
    }

    @Test
    void splitReturnsAMutableListOfParts() {
        assertThat(StringUtil.split("a,b,c", ",")).containsExactly("a", "b", "c");
    }

    @Test
    void getWordsStripsNonWordCharactersFromEachToken() {
        assertThat(StringUtil.getWords("hello, world!")).containsExactly("hello", "world");
    }

    @Test
    void charsetIsIso88591ForLegacyClientCompatibility() {
        assertThat(StringUtil.getCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
    }
}
