package org.alexdev.kepler.server.netty.codec.websocket;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.AttributeKey;
import io.netty.util.NetUtil;

public final class WebSocketClientIp {
    public static final AttributeKey<String> ADDRESS_KEY = AttributeKey.valueOf("websocket.clientIp");
    private static final int MAX_IP_LENGTH = 45;

    private WebSocketClientIp() {
    }

    /**
     * Trust proxy headers only from local peers. Trusted proxies must overwrite
     * these headers before forwarding requests.
     */
    public static String extract(HttpHeaders headers, String socketIp) {
        if (!isTrustedProxy(socketIp)) {
            return socketIp;
        }

        for (String name : new String[]{"CF-Connecting-IP", "X-Forwarded-For", "X-Real-IP"}) {
            String value = headers.get(name);
            if (value == null) {
                continue;
            }
            if (name.equals("X-Forwarded-For")) {
                int comma = value.indexOf(',');
                if (comma >= 0) {
                    value = value.substring(0, comma);
                }
            }
            value = value.trim();
            // Reject scoped/bracketed addresses and ports; never perform DNS lookup.
            if (value.length() <= MAX_IP_LENGTH && value.indexOf('%') < 0
                    && value.indexOf('[') < 0 && value.indexOf(']') < 0
                    && (NetUtil.isValidIpV4Address(value) || NetUtil.isValidIpV6Address(value))) {
                return value;
            }
        }
        return socketIp;
    }

    private static boolean isTrustedProxy(String socketIp) {
        byte[] address = socketIp == null ? null : NetUtil.createByteArrayFromIpAddressString(socketIp);
        if (address == null) {
            return false;
        }

        int offset = 0;
        if (address.length == 16) {
            // Apply IPv4 rules to IPv4-mapped IPv6 peers as well.
            boolean mapped = address[10] == (byte) 0xff && address[11] == (byte) 0xff;
            for (int i = 0; i < 10; i++) {
                mapped &= address[i] == 0;
            }
            if (!mapped) {
                boolean loopback = address[15] == 1;
                for (int i = 0; i < 15; i++) {
                    loopback &= address[i] == 0;
                }
                return loopback || (address[0] & 0xfe) == 0xfc
                        || ((address[0] & 0xff) == 0xfe && (address[1] & 0xc0) == 0x80);
            }
            offset = 12;
        }

        int first = address[offset] & 0xff;
        int second = address[offset + 1] & 0xff;
        return first == 127 || first == 10
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 169 && second == 254);
    }
}
