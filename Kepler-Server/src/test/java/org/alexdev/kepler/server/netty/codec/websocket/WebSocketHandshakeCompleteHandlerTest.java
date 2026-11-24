package org.alexdev.kepler.server.netty.codec.websocket;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketHandshakeCompleteHandlerTest {
    @Test
    @SuppressWarnings("deprecation")
    void waitsForTheDetailedEventThenRecordsTheClientIpAndInstallsNativeHandlers() {
        AtomicInteger configured = new AtomicInteger();
        WebSocketHandshakeCompleteHandler handler = new WebSocketHandshakeCompleteHandler(p -> configured.incrementAndGet());
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // The legacy enum event has no headers: it must not configure the pipeline yet.
        channel.pipeline().fireUserEventTriggered(WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE);
        assertThat(configured.get()).isZero();
        assertThat(channel.pipeline().get(WebSocketHandshakeCompleteHandler.class)).isNotNull();

        HttpHeaders headers = DefaultHttpHeadersFactory.headersFactory().withValidation(false).newHeaders()
                .set("CF-Connecting-IP", "203.0.113.9");
        channel.pipeline().fireUserEventTriggered(new WebSocketServerProtocolHandler.HandshakeComplete("/ws/mus", headers, null));

        assertThat(configured.get()).isEqualTo(1);
        assertThat(channel.pipeline().get(WebSocketHandshakeCompleteHandler.class)).isNull();
        // EmbeddedChannel's peer is not a private proxy address, so the header is not trusted: the socket address is kept.
        assertThat(channel.attr(WebSocketClientIp.ADDRESS_KEY).get()).isNotNull().isNotEqualTo("203.0.113.9");
        channel.finishAndReleaseAll();
    }
}
