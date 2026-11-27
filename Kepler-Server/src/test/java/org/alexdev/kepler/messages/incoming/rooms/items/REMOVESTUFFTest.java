package org.alexdev.kepler.messages.incoming.rooms.items;

import org.alexdev.kepler.messages.MessageHandler;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class REMOVESTUFFTest {
    @Test
    void onlyTheOwnerOrStaffWhoControlAnyRoomDeleteFloorFurni() {
        assertThat(REMOVESTUFF.canRemove(true, false)).isTrue();
        assertThat(REMOVESTUFF.canRemove(false, true)).isTrue();
        assertThat(REMOVESTUFF.canRemove(false, false)).isFalse();
    }

    @Test
    void floorFurniDeleteCommandHasItsOwnHandler() throws Exception {
        // 99 = REMOVESTUFF (floor furni "Borrar"); 85 = REMOVEITEM (wall items only).
        String source = Files.readString(Path.of("src/main/java/org/alexdev/kepler/messages/MessageHandler.java"));

        assertThat(source).contains("registerEvent(99, new REMOVESTUFF());");
        assertThat(source).contains("registerEvent(85, new REMOVEITEM());");
        assertThat(MessageHandler.class).isNotNull();
    }
}
