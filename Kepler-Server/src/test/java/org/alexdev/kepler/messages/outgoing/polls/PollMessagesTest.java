package org.alexdev.kepler.messages.outgoing.polls;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.alexdev.kepler.game.polls.Poll;
import org.alexdev.kepler.game.polls.PollQuestion;
import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.alexdev.kepler.util.encoding.Base64Encoding;
import org.alexdev.kepler.util.encoding.VL64Encoding;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Byte layouts that hh_poll's Poll_Handler_Class reads with GetIntFrom (VL64)
 * and GetStrFrom (chr 2 terminated).
 */
class PollMessagesTest {

    private static String body(MessageComposer composer) {
        ByteBuf buffer = Unpooled.buffer();
        composer.compose(new NettyResponse(composer.getHeader(), buffer));
        return buffer.toString(StandardCharsets.ISO_8859_1);
    }

    private static String header(int id) {
        return new String(Base64Encoding.encode(id, 2), StandardCharsets.ISO_8859_1);
    }

    private static String i(int value) {
        return new String(VL64Encoding.encode(value), StandardCharsets.ISO_8859_1);
    }

    private static String s(String value) {
        return value + (char) 2;
    }

    @Test
    void headersMatchTheHandlerRegistration() {
        assertThat(new POLL_OFFER(1, "x").getHeader()).isEqualTo((short) 316);
        assertThat(new POLL_CONTENTS(new Poll(1, 1, "", "", "", List.of())).getHeader()).isEqualTo((short) 317);
        assertThat(new POLL_ERROR().getHeader()).isEqualTo((short) 318);
    }

    @Test
    void offerIsPollIdThenDescription() {
        assertThat(body(new POLL_OFFER(7, "¿Nos ayudas?\nSon 2 preguntas")))
                .isEqualTo(header(316) + i(7) + s("¿Nos ayudas?\rSon 2 preguntas"));
    }

    @Test
    void errorHasNoBody() {
        assertThat(body(new POLL_ERROR())).isEqualTo(header(318));
    }

    @Test
    void contentsWritesSelectionAndTextQuestionsInOrder() {
        PollQuestion single = new PollQuestion(11, PollQuestion.TYPE_SINGLE, "¿Color?", 1, 1, List.of(100, 101), List.of("Rojo", "Azul"));
        PollQuestion multiple = new PollQuestion(12, PollQuestion.TYPE_MULTIPLE, "¿Juegos?", 1, 2, List.of(200, 201, 202), List.of("A", "B", "C"));
        PollQuestion text = new PollQuestion(13, PollQuestion.TYPE_TEXT, "Opina", 0, 0, List.of(), List.of());
        Poll poll = new Poll(5, 9, "desc", "Encuesta HabboP", "¡Gracias!", List.of(single, multiple, text));

        assertThat(body(new POLL_CONTENTS(poll))).isEqualTo(header(317)
                + i(5) + s("Encuesta HabboP") + s("¡Gracias!") + i(3)
                + i(11) + i(1) + i(1) + s("¿Color?") + i(2) + i(1) + i(1) + s("Rojo") + s("Azul")
                + i(12) + i(2) + i(2) + s("¿Juegos?") + i(3) + i(1) + i(2) + s("A") + s("B") + s("C")
                + i(13) + i(3) + i(3) + s("Opina"));
    }

    @Test
    void selectionLimitsAreClampedToWhatTheClientAccepts() {
        // validateQuestion rejects maxSelect < 1 or minSelect > maxSelect.
        PollQuestion broken = new PollQuestion(1, PollQuestion.TYPE_MULTIPLE, "q", 9, 0, List.of(1, 2), List.of("a", "b"));
        assertThat(broken.getEffectiveMax()).isEqualTo(1);
        assertThat(broken.getEffectiveMin()).isEqualTo(1);

        PollQuestion tooMany = new PollQuestion(1, PollQuestion.TYPE_MULTIPLE, "q", 0, 5, List.of(1, 2, 3), List.of("a", "b", "c"));
        assertThat(tooMany.getEffectiveMax()).isEqualTo(3);
        assertThat(tooMany.getEffectiveMin()).isZero();

        PollQuestion single = new PollQuestion(1, PollQuestion.TYPE_SINGLE, "q", 1, 3, List.of(1, 2, 3), List.of("a", "b", "c"));
        assertThat(single.getEffectiveMax()).isEqualTo(1);
    }

    @Test
    void protocolDelimitersNeverReachTheClient() {
        String evil = "a" + (char) 2 + "b" + (char) 1 + "c\td";

        assertThat(body(new POLL_OFFER(1, evil))).isEqualTo(header(316) + i(1) + s("abcd"));
    }
}
