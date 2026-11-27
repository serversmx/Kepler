package org.alexdev.kepler.messages.outgoing.user;

import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

public class UPDATE_OK extends MessageComposer {
    @Override
    public void compose(NettyResponse response) {
        // handle_updateok reads no payload and calls figureUpdateReady().
    }

    @Override
    public short getHeader() {
        return 211;
    }
}
