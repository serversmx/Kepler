package org.alexdev.kepler.messages.outgoing.rooms.items;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.alexdev.kepler.util.encoding.Base64Encoding;
import org.alexdev.kepler.util.encoding.VL64Encoding;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OneWayDoor_Manager changeStatus (listener 312) reads GetIntFrom twice: furniture id, status.
 * setDoor only accepts 0 and 1.
 */
class ONEWAY_DOOR_STATUSTest {

    private static String compose(ONEWAY_DOOR_STATUS message) {
        ByteBuf buffer = Unpooled.buffer();
        message.compose(new NettyResponse(message.getHeader(), buffer));
        return buffer.toString(StandardCharsets.ISO_8859_1);
    }

    private static String expected(int itemId, int status) {
        return new String(Base64Encoding.encode(312, 2), StandardCharsets.ISO_8859_1)
                + new String(VL64Encoding.encode(itemId), StandardCharsets.ISO_8859_1)
                + new String(VL64Encoding.encode(status), StandardCharsets.ISO_8859_1);
    }

    @Test
    void headerIs312() {
        assertThat(new ONEWAY_DOOR_STATUS(1, true).getHeader()).isEqualTo((short) 312);
    }

    @Test
    void openIsIdThenOne() {
        assertThat(compose(new ONEWAY_DOOR_STATUS(4815, true))).isEqualTo(expected(4815, 1));
    }

    @Test
    void closedIsIdThenZero() {
        assertThat(compose(new ONEWAY_DOOR_STATUS(4815, false))).isEqualTo(expected(4815, 0));
    }
}
