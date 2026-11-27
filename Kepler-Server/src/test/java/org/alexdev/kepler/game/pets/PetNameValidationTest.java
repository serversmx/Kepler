package org.alexdev.kepler.game.pets;

import org.alexdev.kepler.game.moderation.WordfilterManager;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PetNameValidationTest {
    @Test void allowsPrintableNamesWithoutSpacesWithClientLengthLimit() {
        assertThat(PetNameValidation.getNameCheckCode("Mi mascota")).isEqualTo(3);
        assertThat(PetNameValidation.getNameCheckCode("Muñeca!")).isZero();
        // Decomposed accents (input normalisation) are recomposed, not rejected.
        assertThat(PetNameValidation.getNameCheckCode("Mun\u0303eca")).isZero();
        assertThat(PetNameValidation.normalise("Mun\u0303eca")).isEqualTo("Muñeca");
        assertThat(PetNameValidation.getNameCheckCode("x".repeat(15))).isZero();
        assertThat(PetNameValidation.getNameCheckCode("x".repeat(16))).isEqualTo(1);
        assertThat(PetNameValidation.getNameCheckCode("   ")).isEqualTo(2);
        assertThat(PetNameValidation.getNameCheckCode("bad\u0002name")).isEqualTo(3);
        assertThat(PetNameValidation.getNameCheckCode("bad\rname")).isEqualTo(3);
        assertThat(PetNameValidation.getNameCheckCode("bad\u0085name")).isEqualTo(3);
    }
    @Test void rejectsFilteredWords() {
        try (var words = mockStatic(WordfilterManager.class)) {
            words.when(() -> WordfilterManager.filterSentence("badword")).thenReturn("bobba");
            assertThat(PetNameValidation.getNameCheckCode("badword")).isEqualTo(3);
        }
    }
    @Test void finalPurchaseValidationAcceptsTheOwnersNameButNotSpaces() {
        PetManager manager = mock(PetManager.class, CALLS_REAL_METHODS);
        assertThat(manager.isValidName("Existing", "Existing")).isTrue();
        assertThat(manager.isValidName("Existing", "Mi mascota")).isFalse();
        assertThat(manager.isValidName("Existing", "x".repeat(16))).isFalse();
    }
}
