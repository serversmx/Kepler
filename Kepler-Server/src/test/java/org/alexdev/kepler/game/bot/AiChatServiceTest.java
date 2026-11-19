package org.alexdev.kepler.game.bot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiChatServiceTest {
    private final AiChatService service = AiChatService.getInstance();
    private final List<String> menu = List.of("Limonada", "Cafe");

    @Test
    void extractsKnownItemAndStripsTag() {
        var reply = service.toReply("Claro, aqui tienes! [ITEM:limonada]", menu);

        assertEquals("Limonada", reply.item);
        assertEquals("Claro, aqui tienes!", reply.text);
    }

    @Test
    void ignoresItemOutsideMenu() {
        var reply = service.toReply("Te doy cerveza [ITEM:Cerveza]", menu);

        assertNull(reply.item);
        assertEquals("Te doy cerveza", reply.text);
    }

    @Test
    void stripsStageDirectionsAndAsterisks() {
        var reply = service.toReply("*Harry sonrie y se inclina* Hola, bienvenido!", menu);

        assertEquals("Hola, bienvenido!", reply.text);
    }

    @Test
    void cutsLongTextAtSentenceEndWithin100Chars() {
        String raw = "Bienvenido al hotel, aqui puedes jugar y conocer gente nueva. "
                + "Tambien tenemos eventos todos los dias para que te diviertas mucho mas.";
        var reply = service.toReply(raw, menu);

        assertTrue(reply.text.length() <= 100);
        assertEquals("Bienvenido al hotel, aqui puedes jugar y conocer gente nueva.", reply.text);
    }
}
