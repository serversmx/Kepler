package org.alexdev.kepler.server.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.alexdev.kepler.util.encoding.Base64Encoding;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NetworkDecoder runs on the internet-facing game port. A frame whose Base64
 * length prefix decodes to 0 or 1 passes the size guards but is too short for
 * NettyRequest's constructor (which reads a 2-byte header) — that used to throw
 * after the pooled slice was allocated, with no release, a remotely-triggerable
 * ByteBuf leak. The decoder must instead close the channel and emit nothing.
 */
class NetworkDecoderTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void undersizedBodyClosesChannelAndEmitsNothing(int bodyLength) {
        EmbeddedChannel channel = new EmbeddedChannel(new NetworkDecoder());

        // 3-byte Base64 length prefix + padding so the 5-byte header guard passes.
        ByteBuf frame = Unpooled.buffer();
        frame.writeBytes(Base64Encoding.encode(bodyLength, 3));
        frame.writeByte(0x40);
        frame.writeByte(0x40);

        channel.writeInbound(frame);

        assertThat((Object) channel.readInbound())
                .as("no NettyRequest should be emitted for a %d-byte body", bodyLength)
                .isNull();
        assertThat(channel.isOpen())
                .as("the channel must be closed on a malformed frame")
                .isFalse();

        channel.finishAndReleaseAll();
    }
}
