package org.alexdev.kepler.messages.outgoing.rooms;

import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

/**
 * Not sent to clients: the v14 Shockwave client registers no listener for this header and reports
 * "Listener not found" (Sentry). Kept for protocol reference; the HTML5 client only parses it.
 */
public class UPDATE_VOTES extends MessageComposer {
    private final int rating;

    public UPDATE_VOTES(int rating) {
        this.rating = rating;
    }

    @Override
    public void compose(NettyResponse response) {
        response.writeInt(this.rating);
    }

    @Override
    public short getHeader() {
        return 345;
    }
}
