package org.alexdev.kepler.messages.outgoing.catalogue;

import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

public class PURCHASE_ERROR extends MessageComposer {
    @Override
    public void compose(NettyResponse response) {
    }

    @Override
    public short getHeader() {
        return 65;
    }
}
