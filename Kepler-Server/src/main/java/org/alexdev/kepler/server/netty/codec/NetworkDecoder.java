package org.alexdev.kepler.server.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.alexdev.kepler.server.netty.streams.NettyRequest;
import org.alexdev.kepler.util.encoding.Base64Encoding;

import java.util.List;

public class NetworkDecoder extends ByteToMessageDecoder {
    private static final int HEADER_LENGTH = 5;
    private static final int MAX_PACKET_SIZE = 256 * 1024;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) {
        if (buffer.readableBytes() < HEADER_LENGTH) {
            // If the incoming data is less than 5 bytes, it's junk.
            return;
        }

        buffer.markReaderIndex();
        int length = Base64Encoding.decode(new byte[]{buffer.readByte(), buffer.readByte(), buffer.readByte()});

        if (length < 0 || length > MAX_PACKET_SIZE) {
            ctx.close();
            return;
        }

        if (buffer.readableBytes() < length) {
            buffer.resetReaderIndex();
            return;
        }

        // A valid request body always carries at least the 2-byte message
        // header NettyRequest reads in its constructor. A 0/1-byte body would
        // make that constructor throw *after* we've allocated the slice below,
        // and because out.add() never runs the slice would never be released
        // (NettyRequest.dispose() is the only release site) — a remotely
        // triggerable pooled-ByteBuf leak on the internet-facing game port.
        if (length < 2) {
            ctx.close();
            return;
        }

        ByteBuf body = buffer.readBytes(length);

        try {
            out.add(new NettyRequest(body));
        } catch (Exception e) {
            // Construction failed before NettyRequest took ownership, so free
            // the slice here (mirrors MusNetworkDecoder's try/finally release).
            body.release();
            ctx.close();
        }
    }
}
