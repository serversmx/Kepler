package org.alexdev.kepler.server.netty.codec.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketBinaryFrameCodecTest {
    @Test
    void fragmentedBinaryMessageProducesOneCompleteBuffer() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new WebSocketFrameAggregator(65536), new WebSocketBinaryFrameCodec());
        try {
            assertThat(channel.writeInbound(new BinaryWebSocketFrame(false, 0,
                    Unpooled.wrappedBuffer(new byte[]{1, 2})))).isFalse();
            assertThat((Object) channel.readInbound()).isNull();

            assertThat(channel.writeInbound(new ContinuationWebSocketFrame(true, 0,
                    Unpooled.wrappedBuffer(new byte[]{3, 4})))).isTrue();
            ByteBuf message = channel.readInbound();
            try {
                byte[] bytes = new byte[message.readableBytes()];
                message.readBytes(bytes);
                assertThat(bytes).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
                assertThat((Object) channel.readInbound()).isNull();
            } finally {
                message.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
