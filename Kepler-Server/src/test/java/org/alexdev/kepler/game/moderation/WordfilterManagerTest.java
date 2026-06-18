package org.alexdev.kepler.game.moderation;

import org.alexdev.kepler.dao.mysql.WordfilterDao;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WordfilterManager.filterSentence replaces each configured bad word in chat,
 * case-insensitively. The security-relevant detail is Pattern.quote: a filter
 * entry is matched literally, so a word containing regex metacharacters can't
 * turn into a wildcard (or throw). WordfilterDao is statically mocked so the
 * filter map is controlled without a database.
 */
class WordfilterManagerTest {

    private void loadFilter(Map<String, String> words) {
        try (MockedStatic<WordfilterDao> dao = Mockito.mockStatic(WordfilterDao.class)) {
            dao.when(WordfilterDao::getWordfilter).thenReturn(words);
            WordfilterManager.reset(); // recreate the singleton from the mocked map
        }
    }

    @Test
    void replacesConfiguredWordsCaseInsensitively() {
        loadFilter(Map.of("badword", "***"));

        assertThat(WordfilterManager.filterSentence("a BadWord here")).isEqualTo("a *** here");
        assertThat(WordfilterManager.filterSentence("BADWORD")).isEqualTo("***");
    }

    @Test
    void matchesFilterEntriesLiterallyNotAsRegex() {
        // "a.b" must match the literal text "a.b", NOT "axb" — proves Pattern.quote.
        loadFilter(Map.of("a.b", "X"));

        assertThat(WordfilterManager.filterSentence("type a.b now")).isEqualTo("type X now");
        assertThat(WordfilterManager.filterSentence("type axb now")).isEqualTo("type axb now");
    }

    @Test
    void regexMetacharactersInAFilterAreSafe() {
        // A pathological entry must not throw a PatternSyntaxException.
        Map<String, String> words = new LinkedHashMap<>();
        words.put("(bad", "[redacted]");
        loadFilter(words);

        assertThat(WordfilterManager.filterSentence("say (bad please")).isEqualTo("say [redacted] please");
    }

    @Test
    void emptyFilterLeavesTheMessageUntouched() {
        loadFilter(Map.of());

        String message = "nothing to filter here";
        assertThat(WordfilterManager.filterSentence(message)).isEqualTo(message);
    }
}
