package org.alexdev.kepler.messages.outgoing.catalogue;

import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

public class PURCHASE_NOT_ALLOWED extends MessageComposer {
    @Override
    public void compose(NettyResponse response) {
        response.writeInt(1); // v14: catalog_purchase_not_allowed_hc
    }

    @Override
    public short getHeader() {
        return 296;
    }
}
