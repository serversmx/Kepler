package org.alexdev.kepler.messages.outgoing.games;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.alexdev.kepler.messages.incoming.games.GET_SPECTATOR_AMOUNT;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.alexdev.kepler.util.encoding.Base64Encoding;
import org.alexdev.kepler.util.encoding.VL64Encoding;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Room Handler handle_spectator_amount (listener 298) reads GetIntFrom twice:
 * spectator count then maximum; -1 as count removes the counter window.
 */
class SPECTATOR_AMOUNTTest {

    private static String compose(SPECTATOR_AMOUNT message) {
        ByteBuf buffer = Unpooled.buffer();
        message.compose(new NettyResponse(message.getHeader(), buffer));
        return buffer.toString(StandardCharsets.ISO_8859_1);
    }

    private static String expected(int count, int max) {
        return new String(Base64Encoding.encode(298, 2), StandardCharsets.ISO_8859_1)
                + new String(VL64Encoding.encode(count), StandardCharsets.ISO_8859_1)
                + new String(VL64Encoding.encode(max), StandardCharsets.ISO_8859_1);
    }

    @Test
    void headerIs298() {
        assertThat(new SPECTATOR_AMOUNT(0, 0).getHeader()).isEqualTo((short) 298);
    }

    @Test
    void writesCountThenMax() {
        assertThat(compose(new SPECTATOR_AMOUNT(3, 20))).isEqualTo(expected(3, 20));
    }

    @Test
    void negativeCountHidesTheCounter() {
        assertThat(compose(new SPECTATOR_AMOUNT(-1, 0))).isEqualTo(expected(-1, 0));
    }

    @Test
    void maxIsNeverBelowTheRealCount() {
        assertThat(GET_SPECTATOR_AMOUNT.getMax(0)).isEqualTo(GET_SPECTATOR_AMOUNT.DEFAULT_MAX_SPECTATORS);
        assertThat(GET_SPECTATOR_AMOUNT.getMax(35)).isEqualTo(35);
    }
}
