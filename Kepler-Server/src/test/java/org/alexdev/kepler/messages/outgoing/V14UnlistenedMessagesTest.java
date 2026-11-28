package org.alexdev.kepler.messages.outgoing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v14 Shockwave client registers no listener for ROOM_URL (166), UPDATE_VOTES (345), TYPING_STATUS (361)
 * or ROOMEEVENT_INFO (370) (checked in the decompiled Room_Handler_Class and every other hh_* handler); each one
 * it receives is reported as "Listener not found" by Connection Instance Class.forwardMsg.
 */
class V14UnlistenedMessagesTest {
    private static final List<String> UNLISTENED = List.of("ROOM_URL", "UPDATE_VOTES", "TYPING_STATUS", "ROOMEEVENT_INFO");

    @Test
    void serverNeverSendsMessagesTheV14ClientCannotHandle() throws IOException {
        Path root = Path.of("src/main/java");

        try (Stream<Path> files = Files.walk(root)) {
            List<String> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try {
                            String source = Files.readString(path);
                            return UNLISTENED.stream()
                                    .filter(name -> source.contains("new " + name + "("))
                                    .map(name -> root.relativize(path) + " -> " + name);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();

            assertThat(offenders).isEmpty();
        }
    }
}
