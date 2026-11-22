package org.alexdev.kepler.server.rcon;

import org.alexdev.kepler.server.rcon.messages.RconHeader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CMS sends `user_alert` after a HabboLibre vote is credited ("thanks for
 * voting"). The header must resolve and the text must never carry protocol
 * control bytes or be empty.
 */
class RconUserAlertTest {

    @Test
    void userAlertHeaderResolves() {
        assertThat(RconHeader.getByHeader("user_alert")).isEqualTo(RconHeader.USER_ALERT);
        assertThat(RconHeader.getByHeader("USER_ALERT")).isEqualTo(RconHeader.USER_ALERT);
    }

    @Test
    void keepsPlainTextUntouched() {
        assertThat(RconConnectionHandler.cleanUserAlert("¡Gracias por votar por HabboP! Recibiste 50 créditos."))
                .isEqualTo("¡Gracias por votar por HabboP! Recibiste 50 créditos.");
    }

    @Test
    void stripsProtocolControlCharacters() {
        assertThat(RconConnectionHandler.cleanUserAlert("Hola\u0001\u0002 mundo\r\u0000"))
                .isEqualTo("Hola mundo");
    }

    @Test
    void emptyOrMissingTextSendsNothing() {
        assertThat(RconConnectionHandler.cleanUserAlert(null)).isNull();
        assertThat(RconConnectionHandler.cleanUserAlert("   \u0001  ")).isNull();
    }

    @Test
    void longTextIsCapped() {
        String cleaned = RconConnectionHandler.cleanUserAlert("x".repeat(2000));

        assertThat(cleaned).hasSize(RconConnectionHandler.MAX_USER_ALERT_LENGTH);
    }
}
