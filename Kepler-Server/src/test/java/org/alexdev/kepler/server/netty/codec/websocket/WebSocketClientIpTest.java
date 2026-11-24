package org.alexdev.kepler.server.netty.codec.websocket;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaders;
import org.alexdev.kepler.server.netty.NettyPlayerNetwork;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketClientIpTest {
    private static final String SOCKET_IP = "192.168.1.10";

    @ParameterizedTest
    @ValueSource(strings = {"203.0.113.10", "2001:db8::10", "::ffff:203.0.113.10",
            "172.15.255.255", "172.32.0.0", "192.169.0.1", "169.253.255.255",
            "fe7f::1", "fec0::1", "fbff::1", "::", "localhost", "invalid", ""})
    void untrustedPeersIgnoreAllProxyHeaders(String socketIp) {
        HttpHeaders headers = rawHeaders()
                .set("CF-Connecting-IP", "203.0.113.1")
                .set("X-Forwarded-For", "203.0.113.2, 203.0.113.3")
                .set("X-Real-IP", "203.0.113.4");

        assertThat(WebSocketClientIp.extract(headers, socketIp)).isEqualTo(socketIp);
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.255.255.254", "10.0.0.1", "172.16.0.1",
            "172.31.255.255", "192.168.1.10", "169.254.1.1", "::1", "fc00::1", "fdff::1",
            "fe80::1", "febf::1", "::ffff:192.168.1.10", "::ffff:127.0.0.1"})
    void trustedPeersUseProxyHeadersIncludingIpv6(String socketIp) {
        HttpHeaders headers = rawHeaders().set("X-Real-IP", "2001:db8::20");

        assertThat(WebSocketClientIp.extract(headers, socketIp)).isEqualTo("2001:db8::20");
    }

    @Test
    void cloudflareTakesPriorityAndTrimsWhitespace() {
        HttpHeaders headers = rawHeaders()
                .set("CF-Connecting-IP", " 203.0.113.1 ")
                .set("X-Forwarded-For", "203.0.113.2, 203.0.113.3")
                .set("X-Real-IP", "203.0.113.4");

        assertThat(WebSocketClientIp.extract(headers, SOCKET_IP)).isEqualTo("203.0.113.1");
    }

    @Test
    void forwardedForUsesOnlyTheFirstValue() {
        HttpHeaders headers = rawHeaders()
                .set("CF-Connecting-IP", "invalid")
                .set("X-Forwarded-For", " 203.0.113.2 , 203.0.113.3")
                .set("X-Real-IP", "203.0.113.4");

        assertThat(WebSocketClientIp.extract(headers, SOCKET_IP)).isEqualTo("203.0.113.2");
    }

    @Test
    void invalidFirstForwardedValueDoesNotTrustLaterValues() {
        HttpHeaders headers = rawHeaders()
                .set("X-Forwarded-For", "hostname.example, 203.0.113.3")
                .set("X-Real-IP", "203.0.113.4");

        assertThat(WebSocketClientIp.extract(headers, SOCKET_IP)).isEqualTo("203.0.113.4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "localhost", "example.com", "999.2.3.4", "127.1", "0x7f000001",
            "203.0.113.1:80", "[2001:db8::1]", "fe80::1%eth0", "2001:::1",
            "123456789012345678901234567890123456789012345678901234567890",
            "garbage, 203.0.113.1"})
    void invalidHeadersFallBackToSocket(String value) {
        HttpHeaders headers = rawHeaders()
                .set("CF-Connecting-IP", value)
                .set("X-Forwarded-For", value)
                .set("X-Real-IP", value);

        assertThat(WebSocketClientIp.extract(headers, SOCKET_IP)).isEqualTo(SOCKET_IP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2001:db8::1", "::1", "::ffff:192.0.2.1", "203.0.113.10"})
    void acceptsLiteralAddresses(String address) {
        HttpHeaders headers = rawHeaders().set("X-Real-IP", address);

        assertThat(WebSocketClientIp.extract(headers, SOCKET_IP)).isEqualTo(address);
    }

    @Test
    void missingHeadersFallBackToSocket() {
        assertThat(WebSocketClientIp.extract(rawHeaders(), SOCKET_IP)).isEqualTo(SOCKET_IP);
    }

    @Test
    void networkUsesAttributeOnlyWhenPresent() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            String originalIp = NettyPlayerNetwork.getIpAddress(channel);
            channel.attr(WebSocketClientIp.ADDRESS_KEY).set("203.0.113.1");
            assertThat(NettyPlayerNetwork.getIpAddress(channel)).isEqualTo("203.0.113.1");
            channel.attr(WebSocketClientIp.ADDRESS_KEY).set(null);
            assertThat(NettyPlayerNetwork.getIpAddress(channel)).isEqualTo(originalIp);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    /** Header values reach the extractor already parsed; skip Netty's value validation so edge cases can be expressed. */
    private static HttpHeaders rawHeaders() {
        return DefaultHttpHeadersFactory.headersFactory().withValidation(false).newHeaders();
    }
}
