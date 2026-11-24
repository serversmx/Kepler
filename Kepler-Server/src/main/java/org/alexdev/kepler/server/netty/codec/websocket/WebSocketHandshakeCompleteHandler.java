package org.alexdev.kepler.server.netty.codec.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Consumer;

/**
 * Finishes a WebSocket upgrade: records the real client IP from the trusted proxy headers
 * (the MUS login compares it with the game connection's IP, which also comes from those headers)
 * and then installs the native handlers.
 */
public class WebSocketHandshakeCompleteHandler extends ChannelInboundHandlerAdapter {
    private final Consumer<ChannelPipeline> nativeConfigurer;

    public WebSocketHandshakeCompleteHandler(Consumer<ChannelPipeline> nativeConfigurer) {
        this.nativeConfigurer = nativeConfigurer;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        // The legacy enum event carries no headers: wait for the detailed HandshakeComplete.
        if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete complete) {
            String socketIp = socketIp(ctx.channel().remoteAddress());
            ctx.channel().attr(WebSocketClientIp.ADDRESS_KEY).set(WebSocketClientIp.extract(complete.requestHeaders(), socketIp));
            this.nativeConfigurer.accept(ctx.pipeline());
            ctx.pipeline().remove(this);
            return;
        }

        super.userEventTriggered(ctx, event);
    }

    private static String socketIp(SocketAddress address) {
        if (address instanceof InetSocketAddress inet && inet.getAddress() != null) {
            return inet.getAddress().getHostAddress();
        }

        return address == null ? "" : address.toString().replace("/", "").split(":")[0];
    }
}
