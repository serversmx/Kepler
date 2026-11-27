package org.alexdev.kepler.messages.incoming.messenger;

import org.alexdev.kepler.messages.outgoing.recycler.CONFIRM_RECYCLING_RESULT;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MessengerModerationTest {
    @Test
    void sendMessageKeepsOnlyFriendsWithoutDuplicates() {
        Set<Integer> friends = Set.of(2, 3);

        assertThat(MESSENGER_SENDMSG.filterFriends(List.of(2, 9, 3, 2), friends::contains))
                .containsExactly(2, 3);
        assertThat(MESSENGER_SENDMSG.filterFriends(List.of(9), friends::contains)).isEmpty();
    }

    @Test
    void reportTextIncludesSenderIdAndCleansControlChars() {
        String report = MESSENGER_REPORTMESSAGE.buildReport(55, "Pepe", 7, "hola\nmundo");

        assertThat(report).isEqualTo("[Consola] Reporte de mensaje #55 de Pepe (id 7): hola mundo");
    }

    @Test
    void reportTextIsCapped() {
        String report = MESSENGER_REPORTMESSAGE.buildReport(1, null, 7, "x".repeat(1000));

        assertThat(report).endsWith("x".repeat(MESSENGER_REPORTMESSAGE.MAX_TEXT));
        assertThat(report).contains("de ?");
    }

    @Test
    void confirmRecyclingResultUsesHeader307() {
        assertThat(new CONFIRM_RECYCLING_RESULT(true).getHeader()).isEqualTo((short) 307);
    }
}
