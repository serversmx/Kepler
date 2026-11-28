package org.alexdev.kepler.messages.outgoing.rooms;

import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

/**
 * Not sent to clients: the v14 Shockwave client registers no listener for this header and reports
 * "Listener not found" (Sentry). Kept for protocol reference; the HTML5 client only parses it.
 */
public class ROOM_URL extends MessageComposer {

    @Override
    public void compose(NettyResponse response) {
        response.writeString("/client/");
    }

    @Override
    public short getHeader() {
        return 166; // "Bf"
    }
}
