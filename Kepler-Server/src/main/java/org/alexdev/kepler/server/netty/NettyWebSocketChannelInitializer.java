package org.alexdev.kepler.server.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.alexdev.kepler.server.netty.codec.websocket.WebSocketBinaryFrameCodec;
import org.alexdev.kepler.server.netty.codec.websocket.WebSocketClientIp;

public class NettyWebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final NettyServer server;

    public NettyWebSocketChannelInitializer(NettyServer server) {
        this.server = server;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("httpCodec", new HttpServerCodec());
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(65536));
        pipeline.addLast("wsProtocol", new WebSocketServerProtocolHandler(WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/").checkStartsWith(true).maxFramePayloadLength(65536).build()));
        pipeline.addLast("wsHandshakeComplete", new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
                // The legacy enum event has no headers. Wait for the detailed event.
                if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete complete) {
                    String socketIp = channel.remoteAddress().getAddress().getHostAddress();
                    String ip = WebSocketClientIp.extract(complete.requestHeaders(), socketIp);
                    ctx.channel().attr(WebSocketClientIp.ADDRESS_KEY).set(ip);
                    ctx.pipeline().addLast("wsFrameAggregator", new WebSocketFrameAggregator(65536));
                    ctx.pipeline().addLast("wsCodec", new WebSocketBinaryFrameCodec());
                    NettyChannelInitializer.configureGame(ctx.pipeline(), server, true);
                    ctx.pipeline().remove(this);
                    return;
                }
                super.userEventTriggered(ctx, event);
            }
        });
    }
}
