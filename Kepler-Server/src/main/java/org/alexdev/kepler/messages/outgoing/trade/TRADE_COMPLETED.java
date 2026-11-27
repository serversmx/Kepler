package org.alexdev.kepler.messages.outgoing.trade;

import org.alexdev.kepler.messages.types.MessageComposer;
import org.alexdev.kepler.server.netty.streams.NettyResponse;

public class TRADE_COMPLETED extends MessageComposer {
    @Override
    public void compose(NettyResponse response) {
        // handle_trade_completed only calls SafeTrader.complete().
    }

    @Override
    public short getHeader() {
        return 112;
    }
}
