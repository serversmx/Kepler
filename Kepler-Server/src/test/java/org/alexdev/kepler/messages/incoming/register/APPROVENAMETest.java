package org.alexdev.kepler.messages.incoming.register;

import org.alexdev.kepler.dao.mysql.PlayerDao;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Username approval codes returned to the v14 register flow:
 * 0 = ok, 1 = too long (>16), 2 = empty, 3 = bad chars/space/"MOD-", 4 = taken.
 * The "taken" check hits PlayerDao (statically mocked here); the rest is pure
 * validation, including the staff-impersonation block ("MOD-") and the
 * allowed-character whitelist.
 */
class APPROVENAMETest {

    private static final String ALLOWED =
            "1234567890qwertyuiopasdfghjklzxcvbnm-+=?!@:.,$";

    @Test
    void hasAllowedCharactersAcceptsOnlyWhitelistedChars() {
        assertThat(APPROVENAME.hasAllowedCharacters("cool-name123", ALLOWED)).isTrue();
        assertThat(APPROVENAME.hasAllowedCharacters("bad#char", ALLOWED)).isFalse();
        assertThat(APPROVENAME.hasAllowedCharacters("", ALLOWED)).isTrue();
        assertThat(APPROVENAME.hasAllowedCharacters(null, ALLOWED)).isFalse();
    }

    @Test
    void availableNameValidationCodes() {
        try (MockedStatic<PlayerDao> dao = Mockito.mockStatic(PlayerDao.class)) {
            dao.when(() -> PlayerDao.getId(anyString())).thenReturn(0); // name available

            assertThat(APPROVENAME.getNameCheckCode("cool-name")).isZero();
            assertThat(APPROVENAME.getNameCheckCode("a".repeat(17))).isEqualTo(1); // > 16
            assertThat(APPROVENAME.getNameCheckCode("")).isEqualTo(2);             // empty
            assertThat(APPROVENAME.getNameCheckCode("has space")).isEqualTo(3);   // space
            assertThat(APPROVENAME.getNameCheckCode("bad#char")).isEqualTo(3);    // disallowed char
            assertThat(APPROVENAME.getNameCheckCode("MOD-impostor")).isEqualTo(3); // staff impersonation
        }
    }

    @Test
    void takenNameReturnsCodeFour() {
        try (MockedStatic<PlayerDao> dao = Mockito.mockStatic(PlayerDao.class)) {
            dao.when(() -> PlayerDao.getId(anyString())).thenReturn(42); // already exists

            assertThat(APPROVENAME.getNameCheckCode("existinguser")).isEqualTo(4);
        }
    }
}
